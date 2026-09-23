import { useProgress } from '../api/queries'
import { ProgressRing } from './ProgressRing'
import { WeekBars } from './WeekBars'

type Props = {
  planId: number
  date: string
  /** Current plan week, highlighted in the bars. */
  week?: number
}

/**
 * Progress for one plan: the headline percentage, the streak, and a bar per week.
 * The percentage counts what was due up to today — today's unticked items don't
 * count against you until the day is over.
 */
export function ProgressCard({ planId, date, week }: Props) {
  const progress = useProgress(planId, date)

  if (!progress.data) {
    return null
  }
  const { percent, completed, scheduled, streak, weeks } = progress.data

  return (
    <section className="card progress-card">
      <header className="progress-head">
        <ProgressRing percent={percent} />
        <div className="progress-stats">
          <Stat value={streak === 0 ? '—' : String(streak)} label={streak === 1 ? 'day streak' : 'day streak'} />
          <Stat value={`${completed}/${scheduled}`} label="exercises so far" />
        </div>
      </header>

      <WeekBars weeks={weeks} currentWeek={week} />
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
