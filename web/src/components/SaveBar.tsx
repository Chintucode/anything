import { motion } from 'motion/react'

import { formatLong, nextMonday, todayISO } from '../lib/dates'
import { press } from '../motion/springs'

type Props = {
  startDate: string
  onStartDateChange: (iso: string) => void
  onSave: () => void
  busy: boolean
  error?: string
}

/**
 * Floating bar at the bottom of the Add a plan screen: pick a start date, then save.
 * It sits above the tab bar as a translucent layer, with the plan scrolling underneath.
 */
export function SaveBar({ startDate, onStartDateChange, onSave, busy, error }: Props) {
  const quick = [
    { label: 'Today', value: todayISO() },
    { label: 'Next Monday', value: nextMonday() },
  ]

  return (
    <motion.div
      className="save-bar material"
      initial={{ y: 40, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ type: 'spring', bounce: 0, duration: 0.4 }}
    >
      <div className="save-bar-inner">
        <div className="date-row">
          <label htmlFor="start-date" className="t-subhead secondary">Starts</label>
          <input
            id="start-date"
            type="date"
            className="date-input t-headline"
            value={startDate}
            onChange={(e) => onStartDateChange(e.target.value)}
          />
        </div>

        <div className="chip-row">
          {quick.map((q) => (
            <button
              key={q.label}
              className={`chip t-footnote${startDate === q.value ? ' chip-on' : ''}`}
              onClick={() => onStartDateChange(q.value)}
            >
              {q.label}
            </button>
          ))}
          <span className="t-footnote tertiary date-long">{formatLong(startDate)}</span>
        </div>

        {error && <p className="t-footnote save-error">{error}</p>}

        <motion.button className="btn btn-primary save-btn" onClick={onSave} disabled={busy} {...press}>
          {busy ? 'Saving…' : 'Save plan'}
        </motion.button>
      </div>
    </motion.div>
  )
}
