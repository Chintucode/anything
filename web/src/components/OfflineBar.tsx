import { useIsMutating } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'motion/react'

import { useOnline } from '../lib/useOnline'
import { spring } from '../motion/springs'

/**
 * A quiet strip when there's no connection — and an honest one.
 *
 * <p>It used to say "your ticks are saved and sent when you're back", which was not
 * true. An offline tick waits in memory, and memory goes when the app does: tick in
 * a gym with no signal, put the phone in your bag, let the phone close the app, and
 * the tick is gone with no mark and no warning. Being told it was safe is worse than
 * being told nothing, because it's the reason you didn't check.
 *
 * <p>So it says what actually happens, and while ticks are waiting it says how many
 * and what would lose them. Keeping them across a restart is a real feature and is
 * worth building; until it exists, this is the truth.
 */
export function OfflineBar() {
  const online = useOnline()
  const waiting = useIsMutating({ mutationKey: ['setCompletion'] })

  const message = waiting > 0
    ? `Offline · ${waiting} ${waiting === 1 ? 'tick is' : 'ticks are'} waiting — keep the app open`
    : 'Offline · you can look, but ticks need a connection to save'

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
          {message}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
