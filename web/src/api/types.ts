/** Mirrors the JSON the Spring Boot server returns. Keep in sync with the Java records. */

export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface Reps {
  kind: 'COUNT' | 'SECONDS' | 'MAX' | 'TEXT'
  value: number | null
  detail: string
  raw: string
}

export interface ParseError {
  line: number
  message: string
}

// ---- Preview (POST /api/plans/parse): the parser's own shapes

export interface ParsedItem {
  name: string
  sets: number
  reps: Reps
  restSeconds: number | null
  note: string
  line: number
}

/**
 * How a plan is scheduled. WEEKLY: "### Mon: Push", pinned to weekdays and repeating.
 * SEQUENTIAL: "### Day 9: Naming Thoughts", worked through in order, not by date.
 */
export type Schedule = 'WEEKLY' | 'SEQUENTIAL'

export interface ParsedDay {
  /** Weekly plans only. */
  weekday: Weekday | null
  /** Day-by-day plans only: the 9 in "Day 9". */
  dayNumber: number | null
  title: string
  /** The paragraph under the heading. For a meditation day, the practice itself. */
  description: string
  line: number
  items: ParsedItem[]
}

export interface ParsedPhase {
  /** Inclusive, counted in the plan's own unit: weeks when weekly, days when sequential. */
  from: number
  to: number
  name: string
  description: string
  line: number
  days: ParsedDay[]
}

export interface ParsedPlan {
  /** `length` is in weeks for a weekly plan and days for a sequential one. */
  header: { formatVersion: number; title: string; category: string; schedule: Schedule; length: number }
  phases: ParsedPhase[]
}

export interface PreviewResponse {
  ok: boolean
  plan?: ParsedPlan
  errors: ParseError[]
}

// ---- Saved plans

export interface PlanSummary {
  id: number
  title: string
  category: string
  weeks: number
  startDate: string
  endDate: string
  createdAt: string
}

export interface ItemView {
  id: number
  name: string
  sets: number
  reps: Reps
  restSeconds: number | null
  note: string
}

export interface PlanDetail extends PlanSummary {
  dayOffset: number
  phases: {
    id: number
    fromWeek: number
    toWeek: number
    name: string
    days: { id: number; weekday: Weekday; title: string; items: ItemView[] }[]
  }[]
}

// ---- Today and progress

export interface TodayItem extends ItemView {
  done: boolean
  /** What you actually did, when it differed from the plan. */
  actualReps?: number | null
}

export interface TodayResponse {
  planId: number
  planTitle: string
  status: 'TRAINING' | 'REST' | 'SKIPPED' | 'NOT_STARTED' | 'FINISHED'
  date: string
  weekday: Weekday
  week?: number
  totalWeeks: number
  phaseName?: string
  dayTitle?: string
  items?: TodayItem[]
  doneCount?: number
  daysUntilStart?: number
  next?: { date: string; weekday: Weekday; title: string }
  /** The most recent training day left unfinished, if there is one. */
  missed?: { date: string; weekday: Weekday; title: string; done: number; total: number }
  /** Rest days only: true once the day has been marked as taken. */
  rested?: boolean
}

export interface WeekDay {
  date: string
  weekday: Weekday
  status: 'TRAINING' | 'REST' | 'SKIPPED' | 'NOT_STARTED' | 'FINISHED'
  planWeek?: number
  title?: string
  done: number
  total: number
}

export interface WeekResponse {
  planId: number
  from: string
  to: string
  days: WeekDay[]
}

export interface ProgressResponse {
  planId: number
  date: string
  percent: number
  completed: number
  /** Everything the plan asked for up to and including today. `percent` is completed ÷ this. */
  dueSoFar: number
  streak: number
  totalItems: number
  weeks: { week: number; scheduled: number; completed: number }[]
}
