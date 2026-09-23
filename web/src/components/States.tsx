import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { ApiError } from '../api/client'
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

/**
 * Something went wrong. The copy says what actually happened and what to do,
 * because "Error: Failed to fetch" tells you nothing at 6am before a workout.
 */
export function ErrorState({ error, message, onRetry }: {
  error?: unknown
  message?: string
  onRetry: () => void
}) {
  const { title, detail, retryLabel, gone } = describe(error, message)

  return (
    <div className="empty-state" role="alert">
      <h2 className="t-title-3">{title}</h2>
      <p className="t-subhead secondary">{detail}</p>
      <div className="empty-action">
        {gone ? (
          <Link to="/plans" className="btn btn-primary">Go to Plans</Link>
        ) : (
          <button className="btn btn-plain" onClick={onRetry}>{retryLabel}</button>
        )}
      </div>
    </div>
  )
}

function describe(error: unknown, fallback?: string) {
  const offline = typeof navigator !== 'undefined' && !navigator.onLine

  if (offline) {
    return {
      title: 'No connection',
      detail: "You're offline. Anything shows the last workout it saved, and syncs when you're back.",
      retryLabel: 'Try again',
      gone: false,
    }
  }
  if (error instanceof ApiError) {
    if (error.status === 0) {
      return {
        title: "Can't reach the server",
        detail: 'The app is running but the server isn\'t answering. Start it and try again.',
        retryLabel: 'Try again',
        gone: false,
      }
    }
    if (error.status === 404) {
      return {
        title: 'That plan is gone',
        detail: 'It was deleted, maybe on another device.',
        retryLabel: 'Try again',
        gone: true,
      }
    }
    if (error.status >= 500) {
      return {
        title: 'The server had a problem',
        detail: 'Nothing you did. Try again in a moment.',
        retryLabel: 'Try again',
        gone: false,
      }
    }
    return { title: "That didn't work", detail: error.message, retryLabel: 'Try again', gone: false }
  }
  return {
    title: "That didn't work",
    detail: fallback ?? 'Something unexpected happened.',
    retryLabel: 'Try again',
    gone: false,
  }
}

/** Placeholder blocks while loading. Quiet, no spinner. */
export function Skeleton({ rows = 2 }: { rows?: number }) {
  return (
    <div className="skeleton-list" role="status" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton card" />
      ))}
    </div>
  )
}
