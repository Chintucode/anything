import { motion } from 'motion/react'

import type { TodayResponse } from '../api/types'
import { formatShort } from '../lib/dates'
import { shortDay } from '../lib/format'
import { press, spring } from '../motion/springs'

type Props = {
  missed: NonNullable<TodayResponse['missed']>
  onSkip: () => void
  onShift: () => void
  busy: boolean
}

/**
 * The answer to a missed day, offered once, without scolding.
 *
 * Skip writes that day off: it stops counting and doesn't break the streak.
 * Shift moves the whole plan a day later, so you pick up where you left off.
 */
export function MissedCard({ missed, onSkip, onShift, busy }: Props) {
  return (
    <motion.section
      className="card missed-card"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={spring.default}
    >
      <div className="missed-head">
        <span className="day-pill t-caption">{shortDay(missed.weekday)}</span>
        <div className="list-row-text">
          <span className="t-headline">{missed.title} wasn't finished</span>
          <span className="t-subhead secondary">
            {formatShort(missed.date)} · {missed.done} of {missed.total} done
          </span>
        </div>
      </div>

      <div className="missed-actions">
        <motion.button className="btn btn-plain missed-btn" onClick={onSkip} disabled={busy} {...press}>
          Skip it
        </motion.button>
        <motion.button className="btn btn-primary missed-btn" onClick={onShift} disabled={busy} {...press}>
          Shift plan a day
        </motion.button>
      </div>

      <p className="t-footnote secondary missed-note">
        Skip leaves the rest of the plan where it is. Shift moves everything a day later.
      </p>
    </motion.section>
  )
}
