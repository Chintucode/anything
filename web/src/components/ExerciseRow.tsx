import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'

import type { TodayItem } from '../api/types'
import { formatSeconds, formatSetsReps } from '../lib/format'
import { spring } from '../motion/springs'

type Props = {
  item: TodayItem
  onToggle: (done: boolean) => void
  onLogReps: (reps: number | null) => void
}

/**
 * One exercise you can tick off, and log what you actually managed.
 *
 * The whole row is the target (easy with one hand mid-set), it reacts on press
 * rather than on release, and the tick is a spring, not a fade.
 */
export function ExerciseRow({ item, onToggle, onLogReps }: Props) {
  const [logging, setLogging] = useState(false)
  const planned = item.reps.value ?? 0

  function toggle() {
    // A short tap on Android; iOS ignores it. Fires with the visual change, not after.
    navigator.vibrate?.(item.done ? 5 : 12)
    onToggle(!item.done)
  }

  const canLog = item.done && item.reps.kind === 'COUNT'

  return (
    <div className="exercise-wrap">
      <motion.button
        className="exercise-item"
        onClick={toggle}
        whileTap={{ scale: 0.985 }}
        transition={spring.snappy}
        aria-pressed={item.done}
        data-done={item.done}
      >
        <Checkbox done={item.done} />

        <span className="list-row-text">
          <span className="t-body exercise-name">{item.name}</span>
          {item.note && <span className="t-footnote secondary">{item.note}</span>}
        </span>

        <span className="exercise-meta">
          <span className="t-subhead">{formatSetsReps(item.sets, item.reps)}</span>
          {item.restSeconds != null && (
            <span className="t-caption secondary">rest {formatSeconds(item.restSeconds)}</span>
          )}
        </span>
      </motion.button>

      {canLog && (
        <button className="log-toggle t-caption" onClick={() => setLogging((v) => !v)} aria-expanded={logging}>
          {item.actualReps != null ? `did ${item.actualReps}` : 'log actual'}
        </button>
      )}

      <AnimatePresence initial={false}>
        {canLog && logging && (
          <motion.div
            className="log-editor"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={spring.default}
          >
            <div className="log-row">
              <span className="t-footnote secondary">Actually did</span>
              <div className="log-chips">
                {repOptions(planned, item.actualReps ?? null).map((n) => (
                  <button
                    key={n}
                    className={`chip t-footnote${item.actualReps === n ? ' chip-on' : ''}`}
                    onClick={() => { onLogReps(n === planned ? null : n); setLogging(false) }}
                  >
                    {n}
                  </button>
                ))}
              </div>
            </div>
            <p className="t-caption tertiary log-note">
              Plan says {planned}. Logging the real number keeps your report honest.
            </p>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

/** A few sensible numbers around what the plan asked for, plus whatever was logged. */
function repOptions(planned: number, actual: number | null): number[] {
  const set = new Set<number>()
  for (const n of [planned - 3, planned - 2, planned - 1, planned, planned + 1, planned + 2]) {
    if (n > 0) set.add(n)
  }
  if (actual != null) set.add(actual)
  return [...set].sort((a, b) => a - b)
}

function Checkbox({ done }: { done: boolean }) {
  return (
    <span className="check" aria-hidden="true">
      <motion.span
        className="check-fill"
        initial={false}
        animate={{ scale: done ? 1 : 0, opacity: done ? 1 : 0 }}
        transition={spring.snappy}
      />
      <svg width="16" height="16" viewBox="0 0 24 24" className="check-mark">
        <motion.path
          d="M5 12.5l4.5 4.5L19 7.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          initial={false}
          animate={{ pathLength: done ? 1 : 0, opacity: done ? 1 : 0 }}
          transition={{ ...spring.snappy, opacity: { duration: 0.1 } }}
        />
      </svg>
    </span>
  )
}
