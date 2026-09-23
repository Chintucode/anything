import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

import { usePlans, useSetCompletion, useShiftPlan, useSkipDay, useToday } from '../api/queries'
import type { PlanSummary, TodayResponse } from '../api/types'
import { DayPager } from '../components/DayPager'
import { ExerciseRow } from '../components/ExerciseRow'
import { MissedCard } from '../components/MissedCard'
import { ProgressCard } from '../components/ProgressCard'
import { CheckIcon, SparkleIcon } from '../components/Icons'
import { Screen } from '../components/Screen'
import { WeekStrip } from '../components/WeekStrip'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { addDays, formatLong, formatShort, todayISO } from '../lib/dates'
import { plural, shortDay } from '../lib/format'
import { press, spring } from '../motion/springs'

function todayEyebrow() {
  return new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
}

export function TodayScreen() {
  const plans = usePlans()
  const [planId, setPlanId] = useState<number | null>(null)
  // Which day is on screen. Today by default; the week strip can move it.
  const [date, setDate] = useState(todayISO())
  const isToday = date === todayISO()

  // Default to the newest plan, but remember the one the user picked.
  const activeId = planId ?? plans.data?.[0]?.id
  const today = useToday(activeId, date)

  return (
    <Screen title={isToday ? 'Today' : formatShort(date)} eyebrow={isToday ? todayEyebrow() : formatLong(date)}>
      {plans.isPending && <Skeleton rows={3} />}

      {plans.isError && <ErrorState error={plans.error} onRetry={() => plans.refetch()} />}

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

      {activeId !== undefined && (
        <WeekStrip planId={activeId} selected={date} onSelect={setDate} />
      )}

      {!isToday && (
        <div className="other-day-note">
          <span className="t-footnote secondary">Looking at another day</span>
          <button className="chip t-footnote chip-on" onClick={() => setDate(todayISO())}>Back to today</button>
        </div>
      )}

      {activeId !== undefined && today.isPending && <Skeleton rows={4} />}
      {activeId !== undefined && today.isError && (
        <ErrorState error={today.error} onRetry={() => today.refetch()} />
      )}
      {today.data && (
        <>
          <DayPager onChange={(direction) => setDate((d) => addDays(d, direction))}>
            <TodayBody today={today.data} date={date} />
          </DayPager>
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
  return (
    <>
      {today.missed && <MissedAnswer today={today} missed={today.missed} />}
      {body()}
    </>
  )

  function body() {
    switch (today.status) {
      case 'TRAINING':
        return <Workout today={today} date={date} />
      case 'SKIPPED':
        return <SkippedDay today={today} date={date} />
      case 'REST':
        return <RestDay today={today} />
      case 'NOT_STARTED':
        return <NotStarted today={today} />
      default:
        return <Finished today={today} />
    }
  }
}

/** Skip or Shift, shown once, for the most recent day left unfinished. */
function MissedAnswer({ today, missed }: { today: TodayResponse; missed: NonNullable<TodayResponse['missed']> }) {
  const skip = useSkipDay(today.planId)
  const shift = useShiftPlan(today.planId)

  return (
    <MissedCard
      missed={missed}
      busy={skip.isPending || shift.isPending}
      onSkip={() => skip.mutate({ date: missed.date, skipped: true })}
      onShift={() => shift.mutate(1)}
    />
  )
}

/** A day you wrote off. One tap puts it back if you change your mind. */
function SkippedDay({ today, date }: { today: TodayResponse; date: string }) {
  const skip = useSkipDay(today.planId)

  return (
    <section className="card state-card">
      <h2 className="t-title-2">{today.dayTitle} skipped</h2>
      <p className="t-subhead secondary">
        This day doesn't count towards your progress, and it hasn't broken your streak.
      </p>
      <button
        className="btn btn-plain"
        disabled={skip.isPending}
        onClick={() => skip.mutate({ date, skipped: false })}
      >
        Put it back
      </button>
    </section>
  )
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
              onToggle={(value) => setCompletion.mutate({ itemId: item.id, done: value, actualReps: item.actualReps })}
              onLogReps={(reps) => setCompletion.mutate({ itemId: item.id, done: true, actualReps: reps })}
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
  const shift = useShiftPlan(today.planId)

  return (
    <section className="card state-card">
      <h2 className="t-title-2">Starts in {plural(days, 'day')}</h2>
      <p className="t-subhead secondary">{today.planTitle}</p>
      {today.next && (
        <NextUp date={today.next.date} weekday={shortDay(today.next.weekday)} title={today.next.title} />
      )}
      <motion.button
        className="btn btn-primary state-action"
        disabled={shift.isPending}
        onClick={() => shift.mutate(-days)}
        {...press}
      >
        {shift.isPending ? 'Moving…' : 'Start today instead'}
      </motion.button>
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
