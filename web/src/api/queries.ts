import { useQuery } from '@tanstack/react-query'

import { api } from './client'

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
