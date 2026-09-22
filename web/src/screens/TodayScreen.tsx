import { motion } from 'motion/react'
import { Link } from 'react-router'

import { usePlans } from '../api/queries'
import { SparkleIcon } from '../components/Icons'
import { Screen } from '../components/Screen'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { formatShort } from '../lib/dates'
import { press } from '../motion/springs'

function todayEyebrow() {
  return new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
}

/**
 * Day 8: the shell and states. The real workout list arrives on Day 11.
 */
export function TodayScreen() {
  const plans = usePlans()

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

      {plans.isSuccess && plans.data.length > 0 && (
        <section className="card today-placeholder">
          <p className="t-footnote secondary">ACTIVE PLAN</p>
          <h2 className="t-title-3">{plans.data[0].title}</h2>
          <p className="t-subhead secondary">
            {formatShort(plans.data[0].startDate)} – {formatShort(plans.data[0].endDate)}
          </p>
          <p className="t-footnote tertiary placeholder-note">Today's workout list arrives on Day 11.</p>
        </section>
      )}
    </Screen>
  )
}
