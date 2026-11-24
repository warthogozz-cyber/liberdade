import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCategories, useCurrentWorkspace, type MeWorkspace } from '@/shared/api'
import { useBudgets, useCopyBudgets } from '@/shared/api/planning'
import { addMonths, currentMonth } from '@/shared/i18n'
import { useOnline } from '@/shared/offline'
import { BudgetLine } from './BudgetLine'
import {
  markSummaryAnswered,
  monthSummary,
  summaryAnswered,
  summaryStorageKey,
} from './monthSummary'
import './register'

const MAX_LINES = 3

const action =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** The budget on Início: the busiest limits, and at the start of a month how the last one went. */
export default function BudgetHomeCard() {
  const workspace = useCurrentWorkspace()
  return workspace ? <Card workspace={workspace} /> : null
}

function Card({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const online = useOnline()
  const month = currentMonth(new Date(), workspace.timezone)
  const previousMonth = addMonths(month, -1)
  const budgets = useBudgets(workspace.id, month)
  const previous = useBudgets(workspace.id, previousMonth)
  const categories = useCategories(workspace.id, 'EXPENSE')
  const copy = useCopyBudgets(workspace.id)
  const storageKey = summaryStorageKey(workspace.id, month)
  // Also kept here, so the card goes away even when the device will not store the answer.
  const [answered, setAnswered] = useState(() => summaryAnswered(storageKey))
  const editable = workspace.role === 'OWNER' || workspace.role === 'ADULT'

  const lines = budgets.data?.lines ?? []
  const previousLines = previous.data?.lines ?? []
  const names = new Map((categories.data ?? []).map((each) => [each.id, each.name]))
  const busiest = [...lines].sort((a, b) => b.usagePercent - a.usagePercent).slice(0, MAX_LINES)
  const showSummary = !answered && previousLines.length > 0
  const canCopy =
    editable &&
    previousLines.some((each) => !lines.some((line) => line.categoryId === each.categoryId))

  function answer() {
    markSummaryAnswered(storageKey)
    setAnswered(true)
  }

  async function copyLimits() {
    try {
      await copy.mutateAsync(month)
    } catch (error) {
      // The card stays, so the person can try again.
      console.error(error)
      return
    }
    answer()
  }

  if (!showSummary && busiest.length === 0) return null

  const written = intl.formatDate(`${previousMonth}-01T00:00:00Z`, {
    month: 'long',
    timeZone: 'UTC',
  })
  const monthName = written.charAt(0).toLocaleUpperCase(intl.locale) + written.slice(1)
  const { within, total } = monthSummary(previousLines)

  return (
    <section className="flex flex-col gap-3 rounded-card bg-surface p-4">
      <h2 className="text-lg font-semibold">
        <FormattedMessage id="planning.home.title" />
      </h2>
      {showSummary && (
        <div className="flex flex-col gap-3">
          <p>
            {intl.formatMessage(
              { id: 'planning.home.summary' },
              { month: monthName, within, total },
            )}
          </p>
          <div className="flex flex-wrap gap-3">
            {canCopy && (
              <button
                type="button"
                disabled={!online || copy.isPending}
                onClick={() => void copyLimits()}
                className={action}
              >
                <FormattedMessage id="planning.home.copy" />
              </button>
            )}
            <button type="button" onClick={answer} className={action}>
              <FormattedMessage id="planning.home.dismiss" />
            </button>
          </div>
        </div>
      )}
      {busiest.length > 0 && (
        <ul className="flex flex-col gap-2">
          {busiest.map((line) => (
            <li key={line.categoryId}>
              <BudgetLine name={names.get(line.categoryId) ?? ''} line={line} />
            </li>
          ))}
        </ul>
      )}
      <Link
        to="/planejar/orcamento"
        className="flex min-h-12 items-center font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="planning.home.open" />
      </Link>
    </section>
  )
}
