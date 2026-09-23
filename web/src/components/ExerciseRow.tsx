import { motion } from 'motion/react'

import type { TodayItem } from '../api/types'
import { formatSeconds, formatSetsReps } from '../lib/format'
import { spring } from '../motion/springs'

type Props = {
  item: TodayItem
  onToggle: (done: boolean) => void
}

/**
 * One exercise you can tick off.
 *
 * The whole row is the target (easy with one hand mid-set), it reacts on press
 * rather than on release, and the tick is a spring, not a fade.
 */
export function ExerciseRow({ item, onToggle }: Props) {
  function toggle() {
    // A short tap on Android; iOS ignores it. Fires with the visual change, not after.
    navigator.vibrate?.(item.done ? 5 : 12)
    onToggle(!item.done)
  }

  return (
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
  )
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
