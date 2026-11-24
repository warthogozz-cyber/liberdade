import { useIntl } from 'react-intl'
import { useFormatMoney } from '@/shared/api'
import type { GoalView } from '@/shared/api/planning'
import { parseCents } from '@/shared/money'
import './register'

/** "ago/2027" from "2027-08". */
function monthLabel(intl: ReturnType<typeof useIntl>, month: string): string {
  const short = intl.formatDate(`${month}-01T00:00:00Z`, { month: 'short', timeZone: 'UTC' })
  return `${short.replace(/\.$/, '')}/${month.slice(0, 4)}`
}

interface GoalFactsProps {
  goal: GoalView
  /** The name of the account the goal follows, for those who may see it; a Youth never gets one. */
  accountName?: string | undefined
}

/**
 * What a goal says about itself, in words: saved of target, the deadline, the monthly suggestion and
 * the account it follows. The bar is drawn by the card, since only the list has one.
 */
export function GoalFacts({ goal, accountName }: GoalFactsProps) {
  const intl = useIntl()
  const format = useFormatMoney()
  return (
    <>
      <span className="text-sm tabular-nums text-text-muted">
        {intl.formatMessage(
          { id: 'planning.goals.of' },
          {
            saved: format(parseCents(goal.saved.amount)),
            target: format(parseCents(goal.targetAmount.amount)),
          },
        )}
      </span>
      {goal.dueOn && (
        <span className="text-sm text-text-muted">
          {intl.formatMessage(
            { id: 'planning.goals.due' },
            { month: monthLabel(intl, goal.dueOn) },
          )}
        </span>
      )}
      {goal.suggestedMonthly && (
        <span className="text-sm text-text-muted">
          {intl.formatMessage(
            { id: 'planning.goals.suggestion' },
            { amount: format(parseCents(goal.suggestedMonthly.amount)) },
          )}
        </span>
      )}
      {goal.linked && (
        <span className="text-sm text-text-muted">
          {accountName
            ? intl.formatMessage({ id: 'planning.goals.linkedTo' }, { account: accountName })
            : intl.formatMessage({ id: 'planning.goals.linkedFamily' })}
        </span>
      )}
    </>
  )
}
