import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'

import type { Reps, TodayItem } from '../api/types'
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

  function toggle() {
    // A short tap on Android; iOS ignores it. Fires with the visual change, not after.
    navigator.vibrate?.(item.done ? 5 : 12)
    onToggle(!item.done)
  }

  // Anything with a number attached can be logged: reps, a timed hold, or a max
  // set. Only free text ("as many as feels right") has nothing to count.
  const canLog = item.done && item.reps.kind !== 'TEXT'

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
          {item.actualReps != null ? `did ${showValue(item.actualReps, item.reps)}` : 'log actual'}
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
            <LogEditor item={item} onPick={(n) => { onLogReps(n); setLogging(false) }} />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

/**
 * The numbers you're likely to want, one tap away, plus a box for the one we
 * didn't guess. A timed hold steps in fives; a max set has nothing to suggest,
 * so it opens straight onto the box.
 */
function LogEditor({ item, onPick }: { item: TodayItem; onPick: (reps: number | null) => void }) {
  const planned = item.reps.value ?? 0
  const step = stepFor(item.reps, planned)
  const options = repOptions(planned, step, item.actualReps ?? null)
  const [custom, setCustom] = useState('')

  // A long timed practice is typed in minutes, the way a person thinks of it:
  // "8" on a ten-minute meditation means eight minutes, never eight seconds.
  const inMinutes = item.reps.kind === 'SECONDS' && step >= 60
  const unitLabel = item.reps.kind === 'SECONDS' ? (inMinutes ? 'min' : 'sec') : 'other'

  // Reopening on a row that already has a number should start from that number.
  useEffect(() => setCustom(''), [item.id])

  function submitCustom() {
    const n = Number(custom)
    if (Number.isFinite(n) && n > 0) {
      onPick(Math.round(inMinutes ? n * 60 : n))
    }
  }

  return (
    <>
      <div className="log-row">
        <span className="t-footnote secondary">Actually did</span>
        <div className="log-chips">
          {options.map((n) => (
            <button
              key={n}
              className={`chip t-footnote${item.actualReps === n ? ' chip-on' : ''}`}
              onClick={() => onPick(item.actualReps === n ? null : n)}
            >
              {showValue(n, item.reps)}
            </button>
          ))}
          <form
            className="log-custom"
            onSubmit={(e) => { e.preventDefault(); submitCustom() }}
          >
            <input
              className="log-input t-footnote"
              type="number"
              inputMode="numeric"
              min={1}
              step={step}
              placeholder={unitLabel}
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
              aria-label={`Something else you did of ${item.name}`}
            />
            {custom !== '' && (
              <button type="submit" className="chip t-footnote chip-on">Save</button>
            )}
          </form>
        </div>
      </div>
      <p className="t-caption tertiary log-note">
        {planned > 0
          ? `Plan says ${showValue(planned, item.reps)}. `
          : 'The plan says as many as you can. '}
        Logging the real number keeps your report honest. Tap the same one again to clear it.
      </p>
    </>
  )
}

/**
 * How far apart the suggested numbers sit. It has to scale with the thing being
 * logged: five-second steps suit a 30s plank, but on a five-minute meditation they
 * produced "4 min 45s, 4 min 50s, 4 min 55s…" — six chips saying nothing useful.
 */
function stepFor(reps: Reps, planned: number): number {
  if (reps.kind !== 'SECONDS') {
    return 1
  }
  if (planned >= 300) {
    return 60   // 5 min and up: whole minutes
  }
  if (planned >= 60) {
    return 15   // a minute or two: quarter minutes
  }
  return 5
}

/** 12 → "12" for reps, "40s" for a timed hold. */
function showValue(value: number, reps: Reps): string {
  return reps.kind === 'SECONDS' ? formatSeconds(value) : String(value)
}

/**
 * A few sensible numbers around what the plan asked for, plus whatever was logged.
 * Picking the planned number stores it like any other: "I did exactly what it said"
 * is worth recording, and silently clearing it looked like the tap hadn't worked.
 * A max set has no planned number, so it gets no guesses — just the box.
 */
function repOptions(planned: number, step: number, actual: number | null): number[] {
  const set = new Set<number>()
  if (planned > 0) {
    for (const i of [-3, -2, -1, 0, 1, 2]) {
      const n = planned + i * step
      if (n > 0) set.add(n)
    }
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
