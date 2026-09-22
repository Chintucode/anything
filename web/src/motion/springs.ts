import type { Transition } from 'motion/react'

/**
 * Shared spring presets. Use these instead of inventing values per component.
 *
 * Motion's { bounce, duration } maps to Apple's { damping, response }:
 *   bounce 0   = damping 1.0 (critically damped: no overshoot)
 *   bounce 0.2 = damping ~0.8 (a little overshoot)
 * `duration` is roughly Apple's "response": how quickly it gets there.
 *
 * Rule: no bounce unless the user's gesture carried momentum (a flick, a drag release).
 */
export const spring = {
  /** Default for anything that moves or appears. */
  default: { type: 'spring', bounce: 0, duration: 0.4 },
  /** Small, quick UI: tab indicator, toggles, checkmarks. */
  snappy: { type: 'spring', bounce: 0, duration: 0.3 },
  /** Sheets and drawers. */
  sheet: { type: 'spring', bounce: 0.15, duration: 0.35 },
  /** After a flick or drag release only: the gesture earned the overshoot. */
  momentum: { type: 'spring', bounce: 0.2, duration: 0.4 },
} satisfies Record<string, Transition>

/** Press feedback: instant on pointer-down, springs back on release. */
export const press = {
  whileTap: { scale: 0.97 },
  transition: spring.snappy,
} as const
