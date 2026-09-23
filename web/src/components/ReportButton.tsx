import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'

import { api } from '../api/client'
import { copyText } from '../lib/clipboard'
import { press, spring } from '../motion/springs'
import { CheckIcon, CopyIcon } from './Icons'

/**
 * The round trip. Your AI wrote the plan; this hands it back what actually
 * happened — the percentages, the days you skipped, the reps you fell short on —
 * and asks for an adjusted plan in the same format.
 */
export function ReportButton({ planId, date }: { planId: number; date: string }) {
  const [state, setState] = useState<'idle' | 'working' | 'copied' | 'failed'>('idle')

  async function copyReport() {
    setState('working')
    try {
      const text = await api.report(planId, date)
      setState((await copyText(text)) ? 'copied' : 'failed')
    } catch {
      setState('failed')
    }
    setTimeout(() => setState('idle'), 2500)
  }

  const label = {
    idle: 'Report for your AI',
    working: 'Building…',
    copied: 'Copied — paste it to your AI',
    failed: "Couldn't build the report",
  }[state]

  return (
    <div className="report-row">
      <motion.button
        className="btn btn-plain report-btn"
        onClick={copyReport}
        disabled={state === 'working'}
        {...press}
      >
        <AnimatePresence mode="wait" initial={false}>
          <motion.span
            key={state}
            className="copy-btn-inner"
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={spring.snappy}
          >
            {state === 'copied' ? <CheckIcon /> : <CopyIcon />}
            {label}
          </motion.span>
        </AnimatePresence>
      </motion.button>
      <p className="t-caption tertiary report-note">
        Paste it into the chat that wrote this plan and ask for the next block.
      </p>
    </div>
  )
}
