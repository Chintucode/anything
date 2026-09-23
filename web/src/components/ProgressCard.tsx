import { useProgress } from '../api/queries'
import { ProgressRing } from './ProgressRing'
import { ReportButton } from './ReportButton'
import { WeekBars } from './WeekBars'

type Props = {
  planId: number
  date: string
  /** Current plan week: the bar the strip highlights. */
  week?: number
}

/**
 * The plan, as a journey rather than a scoreboard.
 *
 * Three metrics stood here before this one and all three were the same idea:
 * done ÷ due-so-far. How well have you been doing *lately*. That number is
 * redundant early (there's no history, so it just echoes today), inert later
 * (one session is a fiftieth of the data), and it punishes permanently — miss a
 * week in month one and it's capped below 100 for the rest of the plan. Making
 * the window a week only made the punishment expire sooner; it also quietly
 * assumed every plan has weeks, which is a gym-program assumption, not a truth.
 *
 * This is completion: how much of the whole plan is done. It starts at zero on
 * day one and reaches 100% when the plan is finished. It only goes up, it can't
 * be ruined, and it doesn't reset — not on a Monday, not on a plan-week boundary,
 * not ever. It also means the same thing for twelve weeks of calisthenics and for
 * twenty-one days of meditation, which is the point of the app.
 *
 * The day's own ring, up on the workout card, is what moves fast. These two are
 * different in kind — a task and a journey — rather than the same thing at two
 * zoom levels, which is what made the last pair redundant.
 */
export function ProgressCard({ planId, date, week }: Props) {
  const progress = useProgress(planId, date)

  if (!progress.data) {
    return null
  }
  const { streak, weeks } = progress.data

  // Every day of the plan, not only the days up to today: the finish line doesn't
  // move as time passes. Skipped days are already out of both sides.
  const done = weeks.reduce((n, w) => n + w.completed, 0)
  const total = weeks.reduce((n, w) => n + w.scheduled, 0)
  const percent = total > 0 ? Math.round((100 * done) / total) : 0
  const finished = total > 0 && done === total

  return (
    <section className="card progress-card">
      <p className="t-caption secondary progress-eyebrow">The whole plan</p>
      <header className="progress-head">
        <ProgressRing
          percent={percent}
          tone={finished ? 'success' : 'accent'}
          label={`${done} of ${total} exercises in the plan done, ${percent} per cent`}
        />
        <div className="progress-stats">
          <Stat
            value={streak === 0 ? '—' : String(streak)}
            label={streak === 1 ? 'session in a row' : 'sessions in a row'}
          />
        </div>
      </header>

      <WeekBars weeks={weeks} currentWeek={week} />

      <ReportButton planId={planId} date={date} />
    </section>
  )
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div className="progress-stat">
      <span className="stat-value">{value}</span>
      <span className="t-caption secondary">{label}</span>
    </div>
  )
}
