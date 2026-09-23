import { motion } from 'motion/react'

type Props = {
  /** 0–100 */
  percent: number
  size?: number
}

/**
 * One number, drawn as a ring: how much of what was due so far is done.
 * The ring fills from empty on open, so you see the value arrive rather than
 * finding it already there.
 */
export function ProgressRing({ percent, size = 96 }: Props) {
  const stroke = 8
  const radius = (size - stroke) / 2

  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
          <circle
            cx={size / 2} cy={size / 2} r={radius}
            fill="none" stroke="var(--fill)" strokeWidth={stroke}
          />
          <motion.circle
            cx={size / 2} cy={size / 2} r={radius}
            fill="none" stroke="var(--accent)" strokeWidth={stroke} strokeLinecap="round"
            initial={{ pathLength: 0 }}
            animate={{ pathLength: percent / 100 }}
            transition={{ type: 'spring', bounce: 0, duration: 0.8 }}
          />
        </g>
      </svg>
      <div className="ring-value">
        <span className="hero-number">{percent}</span>
        <span className="t-caption secondary">%</span>
      </div>
    </div>
  )
}
