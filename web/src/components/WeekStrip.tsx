import { motion } from 'motion/react'

import { useWeek } from '../api/queries'
import type { WeekDay } from '../api/types'
import { todayISO } from '../lib/dates'
import { shortDay } from '../lib/format'
import { spring } from '../motion/springs'

type Props = {
  planId: number
  /** The date currently being shown. */
  selected: string
  onSelect: (date: string) => void
}

/**
 * Monday-to-Sunday strip above today's workout.
 *
 * Each day shows its own state at a glance — done, part-done, rest, skipped —
 * and tapping one opens it, so a session you forgot to tick is two taps away.
 */
export function WeekStrip({ planId, selected, onSelect }: Props) {
  const week = useWeek(planId, selected)
  const today = todayISO()

  if (!week.data) {
    return <div className="week-strip-placeholder" />
  }

  return (
    <div className="week-strip" role="tablist" aria-label="This week">
      {week.data.days.map((day) => (
        <DayPill
          key={day.date}
          day={day}
          selected={day.date === selected}
          isToday={day.date === today}
          onSelect={() => onSelect(day.date)}
        />
      ))}
    </div>
  )
}

function DayPill({ day, selected, isToday, onSelect }: {
  day: WeekDay
  selected: boolean
  isToday: boolean
  onSelect: () => void
}) {
  const state = pillState(day)

  return (
    <button
      role="tab"
      aria-selected={selected}
      className="day-pill-btn"
      onClick={onSelect}
      aria-label={label(day)}
    >
      <span className="t-caption day-pill-name">{shortDay(day.weekday).slice(0, 1)}</span>
      <span className="day-pill-dot" data-state={state}>
        {day.total > 0 && state === 'partial' && (
          <span className="day-pill-fill" style={{ height: `${(day.done / day.total) * 100}%` }} />
        )}
      </span>
      {selected && (
        <motion.span className="day-pill-underline" layoutId="day-underline" transition={spring.snappy} />
      )}
      {isToday && <span className="day-pill-today" aria-hidden="true" />}
    </button>
  )
}

function pillState(day: WeekDay): 'done' | 'partial' | 'missed' | 'rest' | 'outside' {
  if (day.status === 'REST') return 'rest'
  if (day.status === 'SKIPPED') return 'rest'
  if (day.status !== 'TRAINING') return 'outside'
  if (day.total > 0 && day.done === day.total) return 'done'
  if (day.done > 0) return 'partial'
  return 'missed'
}

function label(day: WeekDay): string {
  if (day.status === 'TRAINING') return `${day.title}: ${day.done} of ${day.total} done`
  if (day.status === 'SKIPPED') return 'Skipped'
  if (day.status === 'REST') return 'Rest day'
  return 'Outside the plan'
}
