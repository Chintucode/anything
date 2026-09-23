import { motion } from 'motion/react'
import { useState } from 'react'

import type { ParseError } from '../api/types'
import { copyText } from '../lib/clipboard'
import { plural } from '../lib/format'
import { spring } from '../motion/springs'
import { WarningIcon } from './Icons'

type Props = {
  errors: ParseError[]
  /** Called with a line number when the user taps an error. */
  onJump: (line: number) => void
}

/** Line-numbered problems. Tapping one selects that line in the paste box. */
export function ParseErrors({ errors, onJump }: Props) {
  const [copied, setCopied] = useState(false)

  async function copyForAi() {
    const text = 'My plan has these problems. Fix them and send the whole plan again in the Anything format:\n'
      + errors.map((e) => `- Line ${e.line}: ${e.message}`).join('\n')
    if (await copyText(text)) {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  return (
    <section className="card errors" aria-live="polite">
      <header className="errors-header">
        <span className="warning-icon"><WarningIcon /></span>
        <h2 className="t-headline">{plural(errors.length, 'thing')} to fix</h2>
      </header>
      <ul className="list">
        {errors.map((e, i) => (
          <motion.li
            key={`${e.line}-${e.message}`}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ ...spring.default, delay: i * 0.03 }}
          >
            <button className="list-row error-row" onClick={() => onJump(e.line)}>
              <span className="line-chip t-caption">Line {e.line}</span>
              <span className="t-subhead error-message">{e.message}</span>
            </button>
          </motion.li>
        ))}
      </ul>
      <div className="errors-footer">
        <p className="t-footnote secondary">Not sure how to fix them? Send them to your AI.</p>
        <button className="btn btn-plain btn-small" onClick={copyForAi}>
          {copied ? 'Copied' : 'Copy for my AI'}
        </button>
      </div>
    </section>
  )
}
