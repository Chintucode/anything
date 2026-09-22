import { motion } from 'motion/react'
import type { ReactNode } from 'react'

import { spring } from '../motion/springs'

type EmptyStateProps = {
  icon: ReactNode
  title: string
  message: string
  action?: ReactNode
}

/** A calm, centred message for "nothing here yet", with one clear next step. */
export function EmptyState({ icon, title, message, action }: EmptyStateProps) {
  return (
    <motion.div
      className="empty-state"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={spring.default}
    >
      <div className="empty-icon">{icon}</div>
      <h2 className="t-title-3">{title}</h2>
      <p className="t-subhead secondary">{message}</p>
      {action && <div className="empty-action">{action}</div>}
    </motion.div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="empty-state">
      <h2 className="t-title-3">Something's not right</h2>
      <p className="t-subhead secondary">{message}</p>
      <div className="empty-action">
        <button className="btn btn-plain" onClick={onRetry}>Try again</button>
      </div>
    </div>
  )
}

/** Placeholder blocks while loading. Quiet, no spinner. */
export function Skeleton({ rows = 2 }: { rows?: number }) {
  return (
    <div className="skeleton-list" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton card" />
      ))}
    </div>
  )
}
