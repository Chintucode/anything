import type {
  PlanDetail,
  PlanSummary,
  PreviewResponse,
  ProgressResponse,
  TodayResponse,
  WeekResponse,
} from './types'

/**
 * A failed request. `message` is always something you can show the user.
 * `body` holds the raw JSON, e.g. a 422 preview with line errors.
 */
export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, message: string, body: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response
  try {
    res = await fetch(`/api${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
    })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that it's running.", null)
  }

  if (res.status === 204) {
    return undefined as T
  }

  const body: unknown = await res.json().catch(() => null)

  if (!res.ok) {
    // Server errors are RFC 9457 problem details: { title, detail, status }.
    const problem = (body ?? {}) as { detail?: string; title?: string }
    throw new ApiError(res.status, problem.detail ?? problem.title ?? 'Something went wrong.', body)
  }
  return body as T
}

/** For endpoints that answer in plain text, like the AI report. */
async function requestText(path: string): Promise<string> {
  let res: Response
  try {
    res = await fetch(`/api${path}`, { headers: { Accept: 'text/plain' } })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that it's running.", null)
  }
  if (!res.ok) {
    throw new ApiError(res.status, 'Could not build the report.', null)
  }
  return res.text()
}

/** Parse preview: 422 is an expected answer (the plan has mistakes), not a failure. */
async function parse(text: string): Promise<PreviewResponse> {
  try {
    return await request<PreviewResponse>('/plans/parse', { method: 'POST', body: JSON.stringify({ text }) })
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) {
      return e.body as PreviewResponse
    }
    throw e
  }
}

export const api = {
  parse,
  listPlans: () => request<PlanSummary[]>('/plans'),
  getPlan: (id: number) => request<PlanDetail>(`/plans/${id}`),
  createPlan: (text: string, startDate: string) =>
    request<PlanDetail>('/plans', { method: 'POST', body: JSON.stringify({ text, startDate }) }),
  deletePlan: (id: number) => request<void>(`/plans/${id}`, { method: 'DELETE' }),
  today: (planId: number, date: string) => request<TodayResponse>(`/plans/${planId}/today?date=${date}`),
  report: (planId: number, date: string) => requestText(`/plans/${planId}/report?date=${date}`),
  week: (planId: number, date: string) => request<WeekResponse>(`/plans/${planId}/week?date=${date}`),
  progress: (planId: number, date: string) => request<ProgressResponse>(`/plans/${planId}/progress?date=${date}`),
  setSkipped: (planId: number, date: string, skipped: boolean) =>
    request<{ planId: number; date: string; skipped: boolean }>(`/plans/${planId}/skips`, {
      method: 'PUT',
      body: JSON.stringify({ date, skipped }),
    }),
  shiftPlan: (planId: number, days: number) =>
    request<PlanDetail>(`/plans/${planId}/shift`, { method: 'POST', body: JSON.stringify({ days }) }),
  setRested: (planId: number, date: string, rested: boolean) =>
    request<{ planId: number; date: string; rested: boolean }>(
      `/plans/${planId}/rests`,
      { method: 'PUT', body: JSON.stringify({ date, rested }) },
    ),

  setCompletion: (planId: number, itemId: number, date: string, done: boolean, actualReps?: number | null) =>
    request<{ itemId: number; date: string; done: boolean; actualReps: number | null }>(
      `/plans/${planId}/completions`,
      { method: 'PUT', body: JSON.stringify({ itemId, date, done, actualReps: actualReps ?? null }) },
    ),
}
