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
  /**
   * The server answered, but not with JSON.
   *
   * <p>Worth its own flag because of what it usually means: a host's "waking up"
   * page, a captive portal, or a proxy, any of which answer a perfectly good
   * request with HTML and a cheerful 200. Treating that as data is how the app
   * ends up rendering nothing at all, with nothing to say about it.
   */
  readonly notJson: boolean

  constructor(status: number, message: string, body: unknown, notJson = false) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
    this.notJson = notJson
  }
}

const NOT_JSON_MESSAGE =
  "The server isn't ready yet — it may be starting up. Give it a moment and try again."

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

  const text = await res.text()
  if (text.trim() === '') {
    // An empty 200: nothing to parse, and nothing that wants parsing.
    if (res.ok) {
      return undefined as T
    }
    throw new ApiError(res.status, 'Something went wrong.', null)
  }

  let body: unknown
  try {
    body = JSON.parse(text)
  } catch {
    // NOT `catch(() => null)`. A null here would sail on as if it were data, and
    // a screen handed null renders neither content, nor a spinner, nor an error.
    throw new ApiError(res.status, NOT_JSON_MESSAGE, text.slice(0, 200), true)
  }

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
  const type = res.headers.get('content-type') ?? ''
  if (!type.startsWith('text/plain')) {
    // Same trap as above: a wake-up page would otherwise become "the report".
    throw new ApiError(res.status, NOT_JSON_MESSAGE, null, true)
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
