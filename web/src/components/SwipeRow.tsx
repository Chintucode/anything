import { motion, useAnimationControls, type PanInfo } from 'motion/react'
import { useRef, type ReactNode } from 'react'

import { spring } from '../motion/springs'

const ACTION_WIDTH = 88          // px of the revealed Delete button
const OPEN_THRESHOLD = 40        // how far you must drag before it stays open
const FLICK_VELOCITY = 400       // px/s: a quick flick opens or closes regardless of distance

/** Where a flick would come to rest, the way iOS scrolling decelerates. */
function project(velocity: number, decelerationRate = 0.998) {
  return (velocity / 1000) * decelerationRate / (1 - decelerationRate)
}

type Props = {
  children: ReactNode
  actionLabel: string
  onAction: () => void
}

/**
 * A row you swipe left to reveal one destructive action.
 *
 * The row tracks your finger 1:1, resists past the edges instead of stopping dead,
 * and when you let go it uses where the flick was *heading*, not just where it
 * ended, so a fast short swipe still opens.
 */
export function SwipeRow({ children, actionLabel, onAction }: Props) {
  const controls = useAnimationControls()
  const isOpen = useRef(false)

  function settle(to: 'open' | 'closed', velocity: number) {
    isOpen.current = to === 'open'
    controls.start({
      x: to === 'open' ? -ACTION_WIDTH : 0,
      transition: { ...spring.momentum, velocity },
    })
  }

  function onDragEnd(_: unknown, info: PanInfo) {
    const projected = info.offset.x + project(info.velocity.x)
    const flicked = Math.abs(info.velocity.x) > FLICK_VELOCITY
    const openIt = flicked
      ? info.velocity.x < 0                       // direction of the flick wins
      : projected < -OPEN_THRESHOLD
    settle(openIt ? 'open' : 'closed', info.velocity.x)
  }

  return (
    <div className="swipe-row">
      <button
        className="swipe-action"
        style={{ width: ACTION_WIDTH }}
        onClick={() => { settle('closed', 0); onAction() }}
      >
        {actionLabel}
      </button>
      <motion.div
        className="swipe-content"
        drag="x"
        dragConstraints={{ left: -ACTION_WIDTH, right: 0 }}
        dragElastic={{ left: 0.25, right: 0 }}   // soft resistance past the end, none at the start
        dragDirectionLock
        animate={controls}
        onDragEnd={onDragEnd}
      >
        {children}
      </motion.div>
    </div>
  )
}
