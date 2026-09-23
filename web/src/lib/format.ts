import type { Reps, Weekday } from '../api/types'

const SHORT_DAYS: Record<Weekday, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
}

export function shortDay(day: Weekday): string {
  return SHORT_DAYS[day]
}

/** 45 → "45s", 90 → "1 min 30s", 120 → "2 min". */
export function formatSeconds(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}s`
  }
  const min = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest === 0 ? `${min} min` : `${min} min ${rest}s`
}

/** 4 sets of 10 → "4 × 10"; "3 × 40s"; "3 × 10 each leg"; "1 × max". */
export function formatSetsReps(sets: number, reps: Reps): string {
  let what: string
  switch (reps.kind) {
    case 'COUNT':
      what = String(reps.value)
      break
    case 'SECONDS':
      what = formatSeconds(reps.value ?? 0)
      break
    case 'MAX':
      what = 'max'
      break
    default:
      what = reps.raw
  }
  if (reps.detail) {
    what += ` ${reps.detail}`
  }
  return `${sets} × ${what}`
}

export function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? '' : 's'}`
}
