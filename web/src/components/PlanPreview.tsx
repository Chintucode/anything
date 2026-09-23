import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'

import type { ParsedDay, ParsedItem, ParsedPhase, ParsedPlan } from '../api/types'
import { formatSeconds, formatSetsReps, plural, shortDay } from '../lib/format'
import { spring } from '../motion/springs'
import { ChevronDown } from './Icons'

/**
 * What the app understood from the pasted plan: the moment the user decides to trust it.
 *
 * Two shapes of plan read differently here. A weekly program is summarised the way
 * a coach would — weeks, days a week, exercises. A day-by-day course is summarised
 * the way the course itself does — days, and how much time it adds up to. Asking
 * "days per week" of a 21-day meditation course would be answering a question
 * nobody asked.
 */
export function PlanPreview({ plan }: { plan: ParsedPlan }) {
  const weekly = plan.header.schedule === 'WEEKLY'
  const days = plan.phases.flatMap((p) => p.days)
  const items = days.flatMap((d) => d.items)

  return (
    <div className="preview">
      <section className="card preview-summary">
        <p className="t-footnote secondary eyebrow">{plan.header.category}</p>
        <h2 className="t-title-2">{plan.header.title}</h2>
        <div className="stat-row">
          <Stat value={String(plan.header.length)} label={weekly ? 'weeks' : 'days'} />
          <Stat value={String(plan.phases.length)} label={plan.phases.length === 1 ? 'phase' : 'phases'} />
          {weekly ? (
            <>
              <Stat value={String(Math.max(...plan.phases.map((p) => p.days.length)))} label="days / week" />
              <Stat value={String(items.length)} label="exercises" />
            </>
          ) : (
            <CourseTotal items={items} />
          )}
        </div>
      </section>

      {plan.phases.map((phase) => (
        <section key={phase.line} className="preview-phase">
          <h3 className="section-label t-footnote secondary">
            {phaseRange(phase, weekly)}
            {phase.name && ` · ${phase.name}`}
          </h3>
          {phase.description && (
            <p className="t-footnote secondary phase-description">{phase.description}</p>
          )}
          <ul className="list card">
            {phase.days.map((day) => (
              <DayRow key={day.line} day={day} weekly={weekly} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function phaseRange(phase: ParsedPhase, weekly: boolean): string {
  const unit = weekly ? 'Week' : 'Day'
  return phase.from === phase.to ? `${unit} ${phase.from}` : `${unit}s ${phase.from}–${phase.to}`
}

/** Seconds of timed work in these items, counting every set. */
function timedSeconds(items: ParsedItem[]): number {
  return items.reduce((sum, i) => sum + (i.reps.kind === 'SECONDS' ? (i.reps.value ?? 0) * i.sets : 0), 0)
}

/** 5 min · 45 min · 3h 30m — how long something adds up to, in the unit a person thinks in. */
function formatTotal(seconds: number): string {
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) {
    return `${minutes} min`
  }
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m === 0 ? `${h}h` : `${h}h ${m}m`
}

/**
 * For a course, the honest summary of effort is time: "3h 30m over 21 days" says more
 * than "25 items". Plans with nothing timed fall back to a count.
 */
function CourseTotal({ items }: { items: ParsedItem[] }) {
  const seconds = timedSeconds(items)
  return seconds > 0
    ? <Stat value={formatTotal(seconds)} label="in total" wide />
    : <Stat value={String(items.length)} label={items.length === 1 ? 'step' : 'steps'} wide />
}

function Stat({ value, label, wide = false }: { value: string; label: string; wide?: boolean }) {
  return (
    <div className={`stat${wide ? ' stat-wide' : ''}`}>
      <span className="stat-value">{value}</span>
      <span className="t-caption secondary">{label}</span>
    </div>
  )
}

/**
 * An untitled day still needs a headline. A weekday session is a workout; a course
 * day is better named by what you'll actually do in it than by a generic word.
 */
function fallbackTitle(day: ParsedDay, weekly: boolean): string {
  return weekly ? 'Workout' : day.items[0]?.name ?? `Day ${day.dayNumber}`
}

/** A day that opens to show what's in it. */
function DayRow({ day, weekly }: { day: ParsedDay; weekly: boolean }) {
  const [open, setOpen] = useState(false)
  const seconds = timedSeconds(day.items)
  const summary = weekly
    ? plural(day.items.length, 'exercise')
    : seconds > 0 ? formatTotal(seconds) : plural(day.items.length, 'step')

  return (
    <li>
      <button className="list-row day-row" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        {day.weekday
          ? <span className="day-pill t-caption">{shortDay(day.weekday)}</span>
          : <span className="day-pill day-pill-number t-caption">Day {day.dayNumber}</span>}
        <span className="list-row-text">
          <span className="t-headline">{day.title || fallbackTitle(day, weekly)}</span>
        </span>
        <span className="t-subhead secondary">{summary}</span>
        <motion.span className="tertiary" animate={{ rotate: open ? 180 : 0 }} transition={spring.snappy}>
          <ChevronDown />
        </motion.span>
      </button>

      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            className="day-detail"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={spring.default}
          >
            {day.description && <p className="t-subhead day-description">{day.description}</p>}
            <ul className="exercise-list">
              {day.items.map((item) => (
                <li key={item.line} className="exercise-row">
                  <div className="list-row-text">
                    <span className="t-body">{item.name}</span>
                    {item.note && <span className="t-footnote secondary">{item.note}</span>}
                  </div>
                  <div className="exercise-meta">
                    <span className="t-subhead">{formatSetsReps(item.sets, item.reps)}</span>
                    {item.restSeconds != null && (
                      <span className="t-caption secondary">rest {formatSeconds(item.restSeconds)}</span>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </motion.div>
        )}
      </AnimatePresence>
    </li>
  )
}
