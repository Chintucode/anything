/**
 * Dates as the server expects them: "YYYY-MM-DD" in the user's LOCAL time zone.
 * Never use toISOString() for this: it converts to UTC, so at 1am in India
 * it would still say yesterday.
 */
export function toISODate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function todayISO(): string {
  return toISODate(new Date())
}

/** "2026-09-28" → "Mon, 28 Sep" in the user's locale. */
export function formatShort(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })
}
