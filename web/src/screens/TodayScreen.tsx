import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

import { usePlans, useSetCompletion, useToday } from '../api/queries'
import type { PlanSummary, TodayResponse } from '../api/types'
import { ExerciseRow } from '../components/ExerciseRow'
import { ProgressCard } from '../components/ProgressCard'
import { CheckIcon, SparkleIcon } from '../components/Icons'
import { Screen } from '../components/Screen'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { formatShort, todayISO } from '../lib/dates'
import { plural, shortDay } from '../lib/format'
import { press, spring } from '../motion/springs'

function todayEyebrow() {
  return new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
}

export function TodayScreen() {
  const plans = usePlans()
  const [planId, setPlanId] = useState<number | null>(null)
  const date = todayISO()

  // Default to the newest plan, but remember the one the user picked.
  const activeId = planId ?? plans.data?.[0]?.id
  const today = useToday(activeId, date)

  return (
    <Screen title="Today" eyebrow={todayEyebrow()}>
      {plans.isPending && <Skeleton rows={3} />}

      {plans.isError && <ErrorState message={plans.error.message} onRetry={() => plans.refetch()} />}

      {plans.isSuccess && plans.data.length === 0 && (
        <EmptyState
          icon={<SparkleIcon />}
          title="No plan yet"
          message="Paste a plan your AI wrote, and Anything turns it into one calm list a day."
          action={
            <motion.span {...press} style={{ display: 'inline-block' }}>
              <Link to="/plans/new" className="btn btn-primary">Add a plan</Link>
            </motion.span>
          }
        />
      )}

      {plans.isSuccess && plans.data.length > 1 && (
        <PlanSwitcher plans={plans.data} activeId={activeId} onPick={setPlanId} />
      )}

      {activeId !== undefined && today.isPending && <Skeleton rows={4} />}
      {activeId !== undefined && today.isError && (
        <ErrorState message={today.error.message} onRetry={() => today.refetch()} />
      )}
      {today.data && (
        <>
          <TodayBody today={today.data} date={date} />
          <ProgressCard planId={today.data.planId} date={date} week={today.data.week} />
        </>
      )}
    </Screen>
  )
}

function PlanSwitcher({ plans, activeId, onPick }: {
  plans: PlanSummary[]
  activeId?: number
  onPick: (id: number) => void
}) {
  return (
    <div className="plan-switcher" role="tablist" aria-label="Choose plan">
      {plans.map((p) => (
        <button
          key={p.id}
          role="tab"
          aria-selected={p.id === activeId}
          className={`chip t-footnote${p.id === activeId ? ' chip-on' : ''}`}
          onClick={() => onPick(p.id)}
        >
          {p.title}
        </button>
      ))}
    </div>
  )
}

function TodayBody({ today, date }: { today: TodayResponse; date: string }) {
  switch (today.status) {
    case 'TRAINING':
      return <Workout today={today} date={date} />
    case 'REST':
      return <RestDay today={today} />
    case 'NOT_STARTED':
      return <NotStarted today={today} />
    default:
      return <Finished today={today} />
  }
}

function Workout({ today, date }: { today: TodayResponse; date: string }) {
  const setCompletion = useSetCompletion(today.planId, date)
  const items = today.items ?? []
  const done = today.doneCount ?? 0
  const allDone = items.length > 0 && done === items.length
  const [celebrated, setCelebrated] = useState(false)

  useEffect(() => {
    if (allDone && !celebrated) {
      setCelebrated(true)
      navigator.vibrate?.([12, 40, 18])
    }
    if (!allDone && celebrated) {
      setCelebrated(false)
    }
  }, [allDone, celebrated])

  return (
    <>
      <section className="card workout-header">
        <div className="workout-header-text">
          <p className="t-footnote secondary eyebrow">
            Week {today.week} of {today.totalWeeks}
            {today.phaseName && ` · ${today.phaseName}`}
          </p>
          <h2 className="t-title-2">{today.dayTitle}</h2>
          <p className="t-subhead secondary">{done} of {items.length} done</p>
        </div>
        <AnimatePresence>
          {allDone && (
            <motion.span
              className="day-done"
              initial={{ scale: 0.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.6, opacity: 0 }}
              transition={spring.momentum}
              aria-label="Day complete"
            >
              <CheckIcon size={20} />
            </motion.span>
          )}
        </AnimatePresence>
        <div className="workout-progress" aria-hidden="true">
          <motion.div
            className="workout-progress-fill"
            initial={false}
            animate={{ scaleX: items.length ? done / items.length : 0 }}
            transition={spring.default}
          />
        </div>
      </section>

      <ul className="list card exercise-items">
        {items.map((item) => (
          <li key={item.id}>
            <ExerciseRow
              item={item}
              onToggle={(value) => setCompletion.mutate({ itemId: item.id, done: value })}
            />
          </li>
        ))}
      </ul>

      <AnimatePresence>
        {allDone && (
          <motion.p
            className="t-subhead done-note"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={spring.default}
          >
            {today.dayTitle} done. See you {today.next ? formatShort(today.next.date) : 'next session'}.
          </motion.p>
        )}
      </AnimatePresence>

      {setCompletion.isError && (
        <p className="t-footnote inline-error">
          Couldn't save that tick. {setCompletion.error.message}
        </p>
      )}
    </>
  )
}

function RestDay({ today }: { today: TodayResponse }) {
  return (
    <section className="card state-card">
      <h2 className="t-title-2">Rest day</h2>
      <p className="t-subhead secondary">
        Week {today.week} of {today.totalWeeks}
        {today.phaseName && ` · ${today.phaseName}`}
      </p>
      {today.next ? (
        <NextUp date={today.next.date} weekday={shortDay(today.next.weekday)} title={today.next.title} />
      ) : (
        <p className="t-subhead secondary">Nothing left to do. The plan finishes here.</p>
      )}
    </section>
  )
}

function NotStarted({ today }: { today: TodayResponse }) {
  const days = today.daysUntilStart ?? 0
  return (
    <section className="card state-card">
      <h2 className="t-title-2">Starts in {plural(days, 'day')}</h2>
      <p className="t-subhead secondary">{today.planTitle}</p>
      {today.next && (
        <NextUp date={today.next.date} weekday={shortDay(today.next.weekday)} title={today.next.title} />
      )}
    </section>
  )
}

function Finished({ today }: { today: TodayResponse }) {
  return (
    <section className="card state-card">
      <h2 className="t-title-2">Plan complete</h2>
      <p className="t-subhead secondary">
        {today.totalWeeks} weeks of {today.planTitle}, done.
      </p>
      <p className="t-subhead secondary">
        Ask your AI for the next block, then paste it in.
      </p>
      <Link to="/plans/new" className="btn btn-primary state-action">Add the next plan</Link>
    </section>
  )
}

function NextUp({ date, weekday, title }: { date: string; weekday: string; title: string }) {
  return (
    <div className="next-up">
      <span className="day-pill t-caption">{weekday}</span>
      <span className="list-row-text">
        <span className="t-headline">{title}</span>
        <span className="t-footnote secondary">{formatShort(date)}</span>
      </span>
    </div>
  )
}
