import { motion, useAnimationControls, useReducedMotion, type PanInfo } from 'motion/react'
import type { ReactNode } from 'react'

import { spring } from '../motion/springs'

/** Where a flick would come to rest — the curve iOS scrolling uses. */
function project(velocity: number, decelerationRate = 0.998) {
  return (velocity / 1000) * decelerationRate / (1 - decelerationRate)
}

type Props = {
  /** Called with -1 (previous day) or +1 (next day). */
  onChange: (direction: number) => void
  children: ReactNode
}

/**
 * Swipe left or right to move a day.
 *
 * The content tracks your finger the whole way, resists at the ends instead of
 * stopping dead, and when you let go it uses where the flick was *heading* rather
 * than where it stopped. Releasing hands the spring your velocity, so there's no
 * seam between the drag and the animation.
 */
export function DayPager({ onChange, children }: Props) {
  const controls = useAnimationControls()
  const reduced = useReducedMotion()

  async function commit(direction: number, velocity: number) {
    const width = window.innerWidth
    if (reduced) {
      onChange(direction)
      return
    }
    // Leave in the direction of travel, then arrive from the other side.
    await controls.start({
      x: -direction * width * 0.3,
      opacity: 0,
      transition: { ...spring.snappy, velocity },
    })
    onChange(direction)
    await controls.start({ x: direction * width * 0.22, opacity: 0, transition: { duration: 0 } })
    controls.start({ x: 0, opacity: 1, transition: spring.default })
  }

  function onDragEnd(_: unknown, info: PanInfo) {
    const width = window.innerWidth
    const projected = info.offset.x + project(info.velocity.x)
    if (projected < -width * 0.22) {
      commit(1, info.velocity.x)
    } else if (projected > width * 0.22) {
      commit(-1, info.velocity.x)
    } else {
      // Not far enough: spring back, carrying the release velocity.
      controls.start({ x: 0, opacity: 1, transition: { ...spring.momentum, velocity: info.velocity.x } })
    }
  }

  return (
    <motion.div
      className="day-pager"
      drag={reduced ? false : 'x'}
      dragDirectionLock
      dragElastic={0.18}          // soft resistance rather than a wall
      dragMomentum={false}
      dragConstraints={{ left: 0, right: 0 }}
      animate={controls}
      onDragEnd={onDragEnd}
    >
      {children}
    </motion.div>
  )
}
