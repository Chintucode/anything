import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

import { usePlans, useSetCompletion, useSetRested, useShiftPlan, useSkipDay, useToday } from '../api/queries'
import type { PlanSummary, TodayResponse } from '../api/types'
import { DayPager } from '../components/DayPager'
import { ExerciseRow } from '../components/ExerciseRow'
import { MissedCard } from '../components/MissedCard'
import { ProgressCard } from '../components/ProgressCard'
import { ProgressRing } from '../components/ProgressRing'
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
          {/* Progress is always "where I am now". Browsing to another day must not
              move the goalposts: look at tomorrow and today's untouched exercises
              would suddenly count as missed. */}
          <ProgressCard planId={today.data.planId} date={todayISO()} week={today.data.week} />
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
  // A missed day is a question about today's training, so it belongs on today's
  // training screen and nowhere else. A rest day is for resting: nothing from
  // yesterday or tomorrow goes on it. An unfinished day answers for itself, at
  // the bottom of its own screen (see Workout).
  const showMissed = date === todayISO() && today.status === 'TRAINING' && today.missed

  return (
    <>
      {showMissed && <MissedAnswer today={today} missed={today.missed!} />}
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
        return <RestDay today={today} date={date} />
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
  // A day that's over and wasn't finished: the answer to it goes here, under the
  // day itself, rather than following you onto other days' screens.
  const unfinishedPastDay = date < todayISO() && items.length > 0 && !allDone
  const dayPercent = items.length ? Math.round((100 * done) / items.length) : 0

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
      {/* This ring is about the day in front of you, not the plan: it fills as you
          tick and starts again on the next day. Open Friday and you see Friday's. */}
      <section className="card workout-header">
        <div className="workout-header-text">
          <p className="t-footnote secondary eyebrow">
            Week {today.week} of {today.totalWeeks}
            {today.phaseName && ` · ${today.phaseName}`}
          </p>
          <h2 className="t-title-2">{today.dayTitle}</h2>
          <p className="t-subhead secondary">
            {allDone ? 'All done' : `${items.length - done} left of ${items.length}`}
          </p>
        </div>
        <ProgressRing
          percent={dayPercent}
          size={68}
          stroke={6}
          tone={allDone ? 'success' : 'accent'}
          label={`${done} of ${items.length} exercises done`}
        >
          <AnimatePresence mode="popLayout" initial={false}>
            {allDone ? (
              <motion.span
                key="check"
                className="day-ring-check"
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0.5, opacity: 0 }}
                transition={spring.momentum}
              >
                <CheckIcon size={24} />
              </motion.span>
            ) : (
              <motion.span
                key="count"
                className="day-ring-count"
                initial={{ scale: 0.8, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0.8, opacity: 0 }}
                transition={spring.snappy}
              >
                {done}
                <span className="day-ring-total">/{items.length}</span>
              </motion.span>
            )}
          </AnimatePresence>
        </ProgressRing>
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
            {/* The green ring already said "done" — this line is only here for what's next. */}
            {today.next ? `See you ${formatShort(today.next.date)}.` : 'That was the last session of the plan.'}
          </motion.p>
        )}
      </AnimatePresence>

      {unfinishedPastDay && (
        <MissedAnswer
          today={today}
          missed={{
            date,
            weekday: today.weekday,
            title: today.dayTitle ?? 'That day',
            done,
            total: items.length,
          }}
        />
      )}

      {setCompletion.isError && (
        <p className="t-footnote inline-error">
          Couldn't save that tick. {setCompletion.error.message}
        </p>
      )}
    </>
  )
}

function RestDay({ today, date }: { today: TodayResponse; date: string }) {
  const rested = today.rested ?? false
  const setRested = useSetRested(today.planId, date)

  return (
    <section className="card state-card">
      <h2 className="t-title-2">Rest day</h2>
      <p className="t-subhead secondary">
        Week {today.week} of {today.totalWeeks}
        {today.phaseName && ` · ${today.phaseName}`}
      </p>

      {/* A day with nothing to do on it doesn't feel like part of the plan. Resting
          when the plan says rest is following the plan, so there's something to tap. */}
      <motion.button
        className={`btn state-action rest-btn${rested ? ' rest-btn-on' : ''}`}
        aria-pressed={rested}
        onClick={() => {
          navigator.vibrate?.(rested ? 5 : 12)
          setRested.mutate(!rested)
        }}
        {...press}
      >
        <AnimatePresence initial={false} mode="popLayout">
          {rested && (
            <motion.span
              key="check"
              className="rest-check"
              initial={{ scale: 0.5, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.5, opacity: 0 }}
              transition={spring.snappy}
            >
              <CheckIcon size={18} />
            </motion.span>
          )}
        </AnimatePresence>
        {rested ? 'Rested' : 'I rested today'}
      </motion.button>

      <p className="t-footnote tertiary">
        {rested ? 'Nothing else today. That was the plan.' : "Nothing is due today — tap when you've taken it."}
      </p>

      {today.next ? (
        <NextUp date={today.next.date} weekday={shortDay(today.next.weekday)} title={today.next.title} />
      ) : (
        <p className="t-subhead secondary">Nothing left to do. The plan finishes here.</p>
      )}

      {setRested.isError && (
        <p className="t-footnote inline-error">Couldn't save that. {setRested.error.message}</p>
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
