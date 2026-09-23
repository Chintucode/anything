import { AnimatePresence, motion } from 'motion/react'
import { useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router'

import { useCreatePlan, useParsePreview } from '../api/queries'
import { CheckIcon, CopyIcon } from '../components/Icons'
import { ParseErrors } from '../components/ParseErrors'
import { PlanPreview } from '../components/PlanPreview'
import { SaveBar } from '../components/SaveBar'
import { Screen } from '../components/Screen'
import { copyText } from '../lib/clipboard'
import { nextMonday } from '../lib/dates'
import { ANYTHING_PROMPT } from '../lib/prompt'
import { useDebouncedValue } from '../lib/useDebouncedValue'
import { press, spring } from '../motion/springs'

/**
 * Paste (or upload) a plan, see exactly what the app understood, pick a start
 * date, and save it. Nothing is saved until you tap Save plan.
 */
export function NewPlanScreen() {
  const [text, setText] = useState('')
  const [startDate, setStartDate] = useState(nextMonday())
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const navigate = useNavigate()
  const createPlan = useCreatePlan()

  // Wait for a short pause in typing before asking the server. A paste lands in one go anyway.
  const debouncedText = useDebouncedValue(text, 300)
  const preview = useParsePreview(debouncedText)
  const hasText = text.trim().length > 0

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) {
      setText(await file.text())
    }
    e.target.value = '' // allow picking the same file again
  }

  /** Select a line in the paste box, so the user sees exactly what to fix. */
  function jumpToLine(line: number) {
    const area = textareaRef.current
    if (!area) return
    const lines = area.value.split('\n')
    const start = lines.slice(0, line - 1).reduce((sum, l) => sum + l.length + 1, 0)
    const end = start + (lines[line - 1]?.length ?? 0)
    area.focus()
    area.setSelectionRange(start, end)
    const lineHeight = parseFloat(getComputedStyle(area).lineHeight) || 20
    area.scrollTop = Math.max(0, (line - 3) * lineHeight)
    area.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  const result = hasText ? preview.data : undefined
  const checking = hasText && (preview.isFetching || debouncedText !== text)

  return (
    <Screen title="Add a plan" back={{ to: '/plans', label: 'Plans' }}>
      <AnimatePresence initial={false}>
        {!hasText && (
          <motion.div
            key="steps"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={spring.default}
            style={{ overflow: 'hidden' }}
          >
            <HowItWorks />
          </motion.div>
        )}
      </AnimatePresence>

      <section className="card paste-card">
        <label htmlFor="plan-text" className="sr-only">Your plan</label>
        <textarea
          id="plan-text"
          ref={textareaRef}
          className="paste-area"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={'Paste your plan here.\n\n---\nanything: 1\ntitle: …'}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
        />
        <div className="paste-toolbar">
          <label className="btn btn-plain btn-small file-btn">
            Upload file
            <input type="file" accept=".md,.txt,text/markdown,text/plain" onChange={onFile} hidden />
          </label>
          <StatusPill checking={checking} errorCount={result?.errors.length} failed={preview.isError && hasText} />
          {hasText && (
            <button className="btn btn-plain btn-small" onClick={() => setText('')}>Clear</button>
          )}
        </div>
      </section>

      {hasText && preview.isError && (
        <p className="t-subhead inline-error">{preview.error.message}</p>
      )}

      <AnimatePresence mode="wait" initial={false}>
        {result && !result.ok && (
          <motion.div key="errors" {...fadeUp}>
            <ParseErrors errors={result.errors} onJump={jumpToLine} />
          </motion.div>
        )}
        {result?.ok && result.plan && (
          <motion.div key="preview" {...fadeUp}>
            <PlanPreview plan={result.plan} />
            <div className="save-bar-spacer" />
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {result?.ok && (
          <SaveBar
            startDate={startDate}
            onStartDateChange={setStartDate}
            busy={createPlan.isPending}
            error={createPlan.isError ? createPlan.error.message : undefined}
            onSave={() =>
              createPlan.mutate(
                { text, startDate },
                { onSuccess: () => navigate('/', { replace: true }) },
              )
            }
          />
        )}
      </AnimatePresence>
    </Screen>
  )
}

const fadeUp = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -4 },
  transition: spring.default,
}

/** Three steps, with the prompt one tap away. */
function HowItWorks() {
  const [copied, setCopied] = useState(false)

  async function copyPrompt() {
    if (await copyText(ANYTHING_PROMPT)) {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  return (
    <section className="card steps">
      <ol className="steps-list">
        <li>
          <span className="step-num t-caption">1</span>
          <div className="step-body">
            <span className="t-headline">Copy the prompt</span>
            <span className="t-subhead secondary">It tells your AI how to write the plan.</span>
          </div>
          <motion.button className="btn btn-primary btn-small copy-btn" onClick={copyPrompt} {...press}>
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={copied ? 'done' : 'copy'}
                className="copy-btn-inner"
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.8 }}
                transition={spring.snappy}
              >
                {copied ? <CheckIcon /> : <CopyIcon />}
                {copied ? 'Copied' : 'Copy'}
              </motion.span>
            </AnimatePresence>
          </motion.button>
        </li>
        <li>
          <span className="step-num t-caption">2</span>
          <div className="step-body">
            <span className="t-headline">Ask your AI</span>
            <span className="t-subhead secondary">Paste the prompt, then say what plan you want.</span>
          </div>
        </li>
        <li>
          <span className="step-num t-caption">3</span>
          <div className="step-body">
            <span className="t-headline">Paste the plan below</span>
            <span className="t-subhead secondary">You'll see a preview straight away.</span>
          </div>
        </li>
      </ol>
    </section>
  )
}

function StatusPill({ checking, errorCount, failed }: { checking: boolean; errorCount?: number; failed: boolean }) {
  let label: string | null = null
  let tone = 'neutral'
  if (checking) {
    label = 'Checking…'
  } else if (failed) {
    label = 'Not checked'
    tone = 'warning'
  } else if (errorCount === 0) {
    label = 'Looks good'
    tone = 'success'
  } else if (errorCount !== undefined) {
    label = errorCount === 1 ? '1 problem' : `${errorCount} problems`
    tone = 'warning'
  }

  return (
    <span className="status-slot" aria-live="polite">
      <AnimatePresence mode="wait" initial={false}>
        {label && (
          <motion.span
            key={label}
            className={`status-pill t-caption tone-${tone}`}
            initial={{ opacity: 0, y: 3 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -3 }}
            transition={spring.snappy}
          >
            {label}
          </motion.span>
        )}
      </AnimatePresence>
    </span>
  )
}
