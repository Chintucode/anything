import { motion } from 'motion/react'
import { useEffect, useState } from 'react'

type Status = 'checking' | 'up' | 'down'

const label: Record<Status, string> = {
  checking: 'Checking server…',
  up: 'Server connected',
  down: 'Server not reachable',
}

export default function App() {
  const [status, setStatus] = useState<Status>('checking')

  useEffect(() => {
    fetch('/api/health')
      .then((res) => setStatus(res.ok ? 'up' : 'down'))
      .catch(() => setStatus('down'))
  }, [])

  return (
    <motion.main
      initial={{ opacity: 0, y: 12, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 300, damping: 30 }}
      style={{
        background: 'var(--surface)',
        borderRadius: 24,
        padding: '40px 32px',
        width: '100%',
        maxWidth: 360,
        textAlign: 'center',
        boxShadow: '0 1px 2px rgba(0,0,0,0.04), 0 8px 32px rgba(0,0,0,0.06)',
      }}
    >
      <h1 style={{ fontSize: 34, fontWeight: 700, letterSpacing: '-0.02em', margin: 0 }}>
        Anything
      </h1>
      <p style={{ color: 'var(--text-secondary)', fontSize: 15, margin: '6px 0 28px' }}>
        Your AI wrote the plan. Anything runs it.
      </p>
      <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 15 }}>
        <motion.span
          animate={{ scale: status === 'checking' ? [1, 1.3, 1] : 1 }}
          transition={{ repeat: status === 'checking' ? Infinity : 0, duration: 1 }}
          style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            background:
              status === 'up' ? 'var(--ok)' : status === 'down' ? 'var(--down)' : 'var(--wait)',
          }}
        />
        {label[status]}
      </div>
    </motion.main>
  )
}
