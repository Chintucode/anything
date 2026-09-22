import { motion } from 'motion/react'
import { Link } from 'react-router'

import { usePlans } from '../api/queries'
import { ChevronRight, PlusIcon, SparkleIcon } from '../components/Icons'
import { Screen } from '../components/Screen'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { formatShort } from '../lib/dates'
import { press, spring } from '../motion/springs'

function AddButton() {
  return (
    <motion.span {...press} style={{ display: 'inline-block' }}>
      <Link to="/plans/new" className="icon-btn" aria-label="Add a plan">
        <PlusIcon />
      </Link>
    </motion.span>
  )
}

export function PlansScreen() {
  const plans = usePlans()

  return (
    <Screen title="Plans" trailing={<AddButton />}>
      {plans.isPending && <Skeleton rows={2} />}

      {plans.isError && <ErrorState message={plans.error.message} onRetry={() => plans.refetch()} />}

      {plans.isSuccess && plans.data.length === 0 && (
        <EmptyState
          icon={<SparkleIcon />}
          title="Your plans live here"
          message="Ask your AI for a plan in the Anything format, then paste it in."
          action={
            <motion.span {...press} style={{ display: 'inline-block' }}>
              <Link to="/plans/new" className="btn btn-primary">Add a plan</Link>
            </motion.span>
          }
        />
      )}

      {plans.isSuccess && plans.data.length > 0 && (
        <ul className="list card">
          {plans.data.map((plan, i) => (
            <motion.li
              key={plan.id}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...spring.default, delay: i * 0.03 }}
            >
              <div className="list-row">
                <div className="list-row-text">
                  <span className="t-headline">{plan.title}</span>
                  <span className="t-subhead secondary">
                    {plan.weeks} weeks · {formatShort(plan.startDate)} – {formatShort(plan.endDate)}
                  </span>
                </div>
                <span className="tertiary"><ChevronRight /></span>
              </div>
            </motion.li>
          ))}
        </ul>
      )}
    </Screen>
  )
}
