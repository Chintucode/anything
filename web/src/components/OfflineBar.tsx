import { AnimatePresence, motion } from 'motion/react'

import { useOnline } from '../lib/useOnline'
import { spring } from '../motion/springs'

/**
 * A quiet strip when there's no connection. Ticks still work: they're held and
 * sent the moment you're back, so a gym with no signal doesn't lose your session.
 */
export function OfflineBar() {
  const online = useOnline()

  return (
    <AnimatePresence>
      {!online && (
        <motion.div
          className="offline-bar t-footnote"
          initial={{ y: -40, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -40, opacity: 0 }}
          transition={spring.default}
          role="status"
        >
          Offline · your ticks are saved and sent when you're back
        </motion.div>
      )}
    </AnimatePresence>
  )
}
