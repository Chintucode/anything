import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from './client'
import type { TodayResponse } from './types'

/**
 * Query keys in one place, so screens and mutations invalidate the same things.
 * e.g. after ticking an item: queryClient.invalidateQueries({ queryKey: keys.today(id, date) })
 */
export const keys = {
  plans: ['plans'] as const,
  plan: (id: number) => ['plans', id] as const,
  today: (id: number, date: string) => ['plans', id, 'today', date] as const,
  progress: (id: number, date: string) => ['plans', id, 'progress', date] as const,
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
 */
export function useSetCompletion(planId: number, date: string) {
  const queryClient = useQueryClient()
  const key = keys.today(planId, date)

  return useMutation({
    mutationFn: ({ itemId, done }: { itemId: number; done: boolean }) =>
      api.setCompletion(planId, itemId, date, done),

    onMutate: async ({ itemId, done }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<TodayResponse>(key)
      if (previous?.items) {
        const items = previous.items.map((i) => (i.id === itemId ? { ...i, done } : i))
        queryClient.setQueryData<TodayResponse>(key, {
          ...previous,
          items,
          doneCount: items.filter((i) => i.done).length,
        })
      }
      return { previous }
    },

    onError: (_error, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(key, context.previous)
      }
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: key })
      queryClient.invalidateQueries({ queryKey: keys.progress(planId, date) })
    },
  })
}
