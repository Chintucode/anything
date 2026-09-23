import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from './client'
import type { TodayResponse } from './types'

/**
 * Query keys in one place, so screens and mutations invalidate the same things.
 * e.g. after ticking an item: queryClient.invalidateQueries({ queryKey: keys.today(id, date) })
 */
/**
 * Keys nest: ['plans', id, 'today', date] sits under ['plans', id], so invalidating
 * the plan refreshes today and progress with it.
 */
export const keys = {
  plans: ['plans'] as const,
  plan: (id: number) => ['plans', id] as const,
  today: (id: number, date: string) => ['plans', id, 'today', date] as const,
  progress: (id: number, date: string) => ['plans', id, 'progress', date] as const,
  week: (id: number, date: string) => ['plans', id, 'week', date] as const,
}

export function usePlans() {
  return useQuery({ queryKey: keys.plans, queryFn: api.listPlans })
}

export function useToday(planId: number | undefined, date: string) {
  return useQuery({
    queryKey: keys.today(planId ?? -1, date),
    queryFn: () => api.today(planId!, date),
    enabled: planId !== undefined,
  })
}

export function useWeek(planId: number | undefined, date: string) {
  return useQuery({
    queryKey: keys.week(planId ?? -1, date),
    queryFn: () => api.week(planId!, date),
    enabled: planId !== undefined,
  })
}

export function useProgress(planId: number | undefined, date: string) {
  return useQuery({
    queryKey: keys.progress(planId ?? -1, date),
    queryFn: () => api.progress(planId!, date),
    enabled: planId !== undefined,
  })
}

/**
 * Live preview of pasted text. Keyed by the text itself, so if you keep typing,
 * an older, slower answer can never overwrite a newer one.
 */
export function useParsePreview(text: string) {
  return useQuery({
    queryKey: ['parse', text],
    queryFn: () => api.parse(text),
    enabled: text.trim().length > 0,
    staleTime: Infinity,          // same text always parses the same way
    placeholderData: (previous) => previous, // keep the last preview on screen while checking
    retry: false,
  })
}

export function useCreatePlan() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ text, startDate }: { text: string; startDate: string }) => api.createPlan(text, startDate),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.plans }),
  })
}

export function useDeletePlan() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.deletePlan(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.plans }),
  })
}

/**
 * Ticking an exercise. The screen updates the moment you tap: the change goes
 * into the cache first, the request follows, and if it fails the tick is rolled
 * back. Waiting for the server before showing a tick feels dead.
 *
 * Ticking fast, or out of order, used to lose ticks. Each save re-fetched the day
 * when it finished, and a re-fetch started by an earlier tap could land *after* a
 * later tap's save, bringing an answer that predated it — the row would untick
 * itself, taking its "log actual" chip with it. So: the server's own reply patches
 * just that row, and the full re-fetch waits until no tick is still in flight.
 */
export function useSetCompletion(planId: number, date: string) {
  const queryClient = useQueryClient()
  const key = keys.today(planId, date)
  // Shared by every tick on this day, so each one can tell whether it's the last.
  const mutationKey = ['setCompletion', planId, date] as const

  /** Change one row in the cached day, leaving the rest of it alone. */
  function patchItem(itemId: number, done: boolean, actualReps: number | null) {
    queryClient.setQueryData<TodayResponse>(key, (previous) => {
      if (!previous?.items) {
        return previous
      }
      const items = previous.items.map((i) => (i.id === itemId ? { ...i, done, actualReps } : i))
      return { ...previous, items, doneCount: items.filter((i) => i.done).length }
    })
  }

  return useMutation({
    mutationKey,
    mutationFn: ({ itemId, done, actualReps }: { itemId: number; done: boolean; actualReps?: number | null }) =>
      api.setCompletion(planId, itemId, date, done, actualReps),

    // Offline: the request is paused (not failed) and fires on reconnect, so the
    // tick you made in the gym survives the walk home.
    networkMode: 'online',
    retry: 2,

    onMutate: async ({ itemId, done, actualReps }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<TodayResponse>(key)
      patchItem(itemId, done, done ? actualReps ?? null : null)
      return { previous }
    },

    // The server has spoken for this row. Write its answer in, but only for this
    // row: other rows may have ticks of their own still on the way.
    onSuccess: (result) => patchItem(result.itemId, result.done, result.actualReps),

    onError: (_error, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(key, context.previous)
      }
    },

    onSettled: () => {
      // isMutating counts this one too, so 1 means "I'm the last one standing".
      if (queryClient.isMutating({ mutationKey }) > 1) {
        return
      }
      // Everything under ['plans', id]: this day, the week strip, and progress —
      // which is keyed by today, not by the day being ticked.
      queryClient.invalidateQueries({ queryKey: keys.plan(planId) })
    },
  })
}

/** Skipping a day, or putting it back. */
export function useSkipDay(planId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ date, skipped }: { date: string; skipped: boolean }) =>
      api.setSkipped(planId, date, skipped),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.plan(planId) }),
  })
}

/**
 * Marking a rest day as taken. Optimistic, like a tick: the plan asked for nothing
 * and you're confirming you gave it exactly that, so it should land instantly.
 */
export function useSetRested(planId: number, date: string) {
  const queryClient = useQueryClient()
  const key = keys.today(planId, date)

  return useMutation({
    mutationFn: (rested: boolean) => api.setRested(planId, date, rested),
    networkMode: 'online',
    onMutate: async (rested) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<TodayResponse>(key)
      queryClient.setQueryData<TodayResponse>(key, (p) => (p ? { ...p, rested } : p))
      return { previous }
    },
    onError: (_error, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(key, context.previous)
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: keys.plan(planId) }),
  })
}

/** Moving the whole plan: +1 pushes it a day later, -5 starts it five days earlier. */
export function useShiftPlan(planId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (days: number) => api.shiftPlan(planId, days),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.plans })
      queryClient.invalidateQueries({ queryKey: keys.plan(planId) })
    },
  })
}
