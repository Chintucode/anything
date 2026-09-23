import { AnimatePresence, motion, type PanInfo } from 'motion/react'
import { useEffect } from 'react'

import { spring } from '../motion/springs'

type Props = {
  open: boolean
  title: string
  message?: string
  confirmLabel: string
  destructive?: boolean
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * A sheet that slides up from the bottom for a decision that can't be undone.
 * The scrim dims what's behind it: this is a blocking choice, so the background
 * steps back rather than staying available.
 */
export function ConfirmSheet({
  open, title, message, confirmLabel, destructive, busy, onConfirm, onCancel,
}: Props) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onCancel()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            className="scrim"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onCancel}
          />
          <motion.div
            className="sheet card"
            role="alertdialog"
            aria-modal="true"
            aria-label={title}
            initial={{ y: '110%' }}
            animate={{ y: 0 }}
            exit={{ y: '110%' }}
            transition={spring.sheet}
            // Drag it down to dismiss: a flick counts even if it barely moved.
            drag="y"
            dragDirectionLock
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0.02, bottom: 0.6 }}
            onDragEnd={(_: unknown, info: PanInfo) => {
              if (info.offset.y > 90 || info.velocity.y > 500) {
                onCancel()
              }
            }}
          >
            <span className="sheet-grabber" aria-hidden="true" />
            <h2 className="t-title-3">{title}</h2>
            {message && <p className="t-subhead secondary">{message}</p>}
            <div className="sheet-actions">
              <button
                className={`btn ${destructive ? 'btn-danger' : 'btn-primary'}`}
                onClick={onConfirm}
                disabled={busy}
              >
                {busy ? 'Working…' : confirmLabel}
              </button>
              <button className="btn btn-plain" onClick={onCancel} disabled={busy}>Cancel</button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
