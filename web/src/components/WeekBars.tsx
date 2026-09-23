import { motion } from 'motion/react'
import { useState } from 'react'

import { spring } from '../motion/springs'

type Week = { week: number; scheduled: number; completed: number }

/**
 * One bar per week: the track is what's scheduled, the fill is what you did.
 * Tapping a bar names it below, instead of putting a number on every bar.
 */
export function WeekBars({ weeks, currentWeek }: { weeks: Week[]; currentWeek?: number }) {
  const [picked, setPicked] = useState<number | null>(null)
  const shown = weeks.find((w) => w.week === picked)

  return (
    <div className="week-bars">
      <div className="bars-row">
        {weeks.map((w) => {
          const ratio = w.scheduled > 0 ? w.completed / w.scheduled : 0
          const isNow = w.week === currentWeek
          return (
            <button
              key={w.week}
              className={`bar-slot${picked === w.week ? ' bar-picked' : ''}`}
              onClick={() => setPicked(picked === w.week ? null : w.week)}
              aria-label={`Week ${w.week}: ${w.completed} of ${w.scheduled} done`}
              title={`Week ${w.week}: ${w.completed} of ${w.scheduled}`}
            >
              <span className={`bar-track${isNow ? ' bar-now' : ''}`}>
                <motion.span
                  className="bar-fill"
                  initial={{ height: 0 }}
                  animate={{ height: `${ratio * 100}%` }}
                  transition={{ ...spring.default, delay: w.week * 0.015 }}
                />
              </span>
              <span className="t-caption bar-label">{w.week}</span>
            </button>
          )
        })}
      </div>
      <p className="t-footnote secondary bars-caption">
        {shown
          ? `Week ${shown.week}: ${shown.completed} of ${shown.scheduled} exercises done`
          : 'Tap a week to see it'}
      </p>
    </div>
  )
}
