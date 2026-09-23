import { motion } from 'motion/react'
import { useState } from 'react'
import { Link } from 'react-router'

import type { PlanSummary } from '../api/types'
import { useDeletePlan, usePlans } from '../api/queries'
import { ConfirmSheet } from '../components/ConfirmSheet'
import { PlusIcon, SparkleIcon } from '../components/Icons'
import { Screen } from '../components/Screen'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { SwipeRow } from '../components/SwipeRow'
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
  const deletePlan = useDeletePlan()
  const [pendingDelete, setPendingDelete] = useState<PlanSummary | null>(null)

  function confirmDelete() {
    if (!pendingDelete) return
    deletePlan.mutate(pendingDelete.id, { onSettled: () => setPendingDelete(null) })
  }

  return (
    <Screen title="Plans" trailing={<AddButton />}>
      {plans.isPending && <Skeleton rows={2} />}

      {plans.isError && <ErrorState error={plans.error} onRetry={() => plans.refetch()} />}

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
        <>
          <ul className="list card">
            {plans.data.map((plan, i) => (
              <motion.li
                key={plan.id}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ ...spring.default, delay: i * 0.03 }}
              >
                <SwipeRow actionLabel="Delete" onAction={() => setPendingDelete(plan)}>
                  <div className="list-row">
                    <div className="list-row-text">
                      <span className="t-headline">{plan.title}</span>
                      <span className="t-subhead secondary">
                        {plan.weeks} weeks · {formatShort(plan.startDate)} – {formatShort(plan.endDate)}
                      </span>
                    </div>
                  </div>
                </SwipeRow>
              </motion.li>
            ))}
          </ul>
          <p className="t-footnote tertiary list-hint">Swipe a plan left to delete it.</p>
        </>
      )}

      <ConfirmSheet
        open={pendingDelete !== null}
        title={`Delete "${pendingDelete?.title ?? ''}"?`}
        message="This also deletes everything you've ticked off in it. It can't be undone."
        confirmLabel="Delete plan"
        destructive
        busy={deletePlan.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </Screen>
  )
}
