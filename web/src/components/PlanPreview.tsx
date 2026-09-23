import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'

import type { ParsedDay, ParsedPlan } from '../api/types'
import { formatSeconds, formatSetsReps, plural, shortDay } from '../lib/format'
import { spring } from '../motion/springs'
import { ChevronDown } from './Icons'

/** What the app understood from the pasted plan: the moment the user decides to trust it. */
export function PlanPreview({ plan }: { plan: ParsedPlan }) {
  const exercises = plan.phases.reduce(
    (sum, p) => sum + p.days.reduce((s, d) => s + d.items.length, 0), 0)
  const daysPerWeek = Math.max(...plan.phases.map((p) => p.days.length))

  return (
    <div className="preview">
      <section className="card preview-summary">
        <p className="t-footnote secondary eyebrow">{plan.header.category}</p>
        <h2 className="t-title-2">{plan.header.title}</h2>
        <div className="stat-row">
          <Stat value={plan.header.weeks} label="weeks" />
          <Stat value={plan.phases.length} label={plan.phases.length === 1 ? 'phase' : 'phases'} />
          <Stat value={daysPerWeek} label="days / week" />
          <Stat value={exercises} label="exercises" />
        </div>
      </section>

      {plan.phases.map((phase) => (
        <section key={phase.line} className="preview-phase">
          <h3 className="section-label t-footnote secondary">
            {phase.fromWeek === phase.toWeek ? `Week ${phase.fromWeek}` : `Weeks ${phase.fromWeek}–${phase.toWeek}`}
            {phase.name && ` · ${phase.name}`}
          </h3>
          <ul className="list card">
            {phase.days.map((day) => (
              <DayRow key={day.line} day={day} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div className="stat">
      <span className="stat-value">{value}</span>
      <span className="t-caption secondary">{label}</span>
    </div>
  )
}

/** A day that opens to show its exercises. */
function DayRow({ day }: { day: ParsedDay }) {
  const [open, setOpen] = useState(false)

  return (
    <li>
      <button className="list-row day-row" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <span className="day-pill t-caption">{shortDay(day.weekday)}</span>
        <span className="list-row-text">
          <span className="t-headline">{day.title || 'Workout'}</span>
        </span>
        <span className="t-subhead secondary">{plural(day.items.length, 'exercise')}</span>
        <motion.span className="tertiary" animate={{ rotate: open ? 180 : 0 }} transition={spring.snappy}>
          <ChevronDown />
        </motion.span>
      </button>

      <AnimatePresence initial={false}>
        {open && (
          <motion.ul
            className="exercise-list"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={spring.default}
          >
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
          </motion.ul>
        )}
      </AnimatePresence>
    </li>
  )
}
