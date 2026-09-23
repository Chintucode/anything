import { motion } from 'motion/react'
import type { ReactNode } from 'react'

type Props = {
  /** 0–100 */
  percent: number
  size?: number
  stroke?: number
  /** Green once a thing is finished; accent while it's in progress. */
  tone?: 'accent' | 'success'
  /** What sits in the middle. Defaults to the percentage. */
  children?: ReactNode
  label?: string
}

/**
 * One number, drawn as a ring: how much of something is done.
 * The ring fills from empty on open, so you see the value arrive rather than
 * finding it already there.
 *
 * Used at two sizes and two scopes — a small one on the day's card for the
 * session in front of you, a large one on the progress card for the whole plan.
 */
export function ProgressRing({ percent, size = 96, stroke = 8, tone = 'accent', children, label }: Props) {
  const radius = (size - stroke) / 2
  const color = tone === 'success' ? 'var(--success)' : 'var(--accent)'

  return (
    <div className="ring" style={{ width: size, height: size }} role="img" aria-label={label ?? `${percent}% done`}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
          <circle
            cx={size / 2} cy={size / 2} r={radius}
            fill="none" stroke="var(--fill)" strokeWidth={stroke}
          />
          <motion.circle
            cx={size / 2} cy={size / 2} r={radius}
            fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
            initial={{ pathLength: 0 }}
            animate={{ pathLength: percent / 100 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.8 }}
          />
        </g>
      </svg>
      <div className="ring-value" aria-hidden="true">
        {children ?? (
          <>
            <span className="hero-number">{percent}</span>
            <span className="t-caption secondary">%</span>
          </>
        )}
      </div>
    </div>
  )
}
