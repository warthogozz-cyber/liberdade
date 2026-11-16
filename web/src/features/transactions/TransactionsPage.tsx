import { useMemo } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import {
  useAccounts,
  useCategories,
  useCurrentWorkspace,
  useMe,
  useTransactions,
  type MeWorkspace,
} from '@/shared/api'
import { addDays, localDate } from '@/shared/i18n'
import { useOnline, useOutbox } from '@/shared/offline'
import { DataAsOf, useDocumentTitle } from '@/shared/ui'
import { groupByDay, mergeTransactions } from './listItems'
import { TransactionRow } from './TransactionRow'

/** The month's transactions with the user's whole queue, by day (wireframe 6). */
export function TransactionsPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'nav.transactions' })
  useDocumentTitle(title)
  const online = useOnline()
  const user = useMe().data?.user
  const workspace = useCurrentWorkspace()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {!online && (
        <div role="status" className="rounded-card bg-surface p-4">
          <p>
            <FormattedMessage id="transactions.offline" />
          </p>
          {workspace && <TransactionsAsOf workspace={workspace} />}
        </div>
      )}
      {user && workspace ? (
        <TransactionList userId={user.id} displayName={user.displayName} workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

/** When the cached month shown offline was loaded, once there is one (FR-I3). */
function TransactionsAsOf({ workspace }: { workspace: MeWorkspace }) {
  const today = localDate(new Date(), workspace.timezone)
  const transactions = useTransactions(workspace.id, today.slice(0, 7))
  if (!transactions.data) return null
  return (
    <p className="mt-1 text-sm text-text-muted">
      <DataAsOf updatedAt={transactions.dataUpdatedAt} timeZone={workspace.timezone} />
    </p>
  )
}

interface TransactionListProps {
  userId: string
  displayName: string
  workspace: MeWorkspace
}

function TransactionList({ userId, displayName, workspace }: TransactionListProps) {
  const intl = useIntl()
  const today = localDate(new Date(), workspace.timezone)
  const yesterday = addDays(today, -1)
  const transactions = useTransactions(workspace.id, today.slice(0, 7))
  const outbox = useOutbox(userId, workspace.id)
  const expenseCategories = useCategories(workspace.id, 'EXPENSE').data
  const incomeCategories = useCategories(workspace.id, 'INCOME').data
  const accountList = useAccounts(workspace.id).data
  const categories = useMemo(
    () =>
      new Map(
        [...(expenseCategories ?? []), ...(incomeCategories ?? [])].map((each) => [each.id, each]),
      ),
    [expenseCategories, incomeCategories],
  )
  const accounts = useMemo(
    () => new Map((accountList ?? []).map((each) => [each.id, each])),
    [accountList],
  )
  const groups = groupByDay(mergeTransactions(transactions.data, outbox ?? []))

  function dayTitle(date: string): string {
    if (date === today) return intl.formatMessage({ id: 'common.today' })
    if (date === yesterday) return intl.formatMessage({ id: 'common.yesterday' })
    return intl.formatDate(`${date}T00:00:00Z`, { day: 'numeric', month: 'long', timeZone: 'UTC' })
  }

  return (
    <>
      {!transactions.data && transactions.isError && (
        <div className="flex flex-col items-start gap-2">
          <p>
            <FormattedMessage id="common.error" />
          </p>
          <button
            type="button"
            onClick={() => void transactions.refetch()}
            className="min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      )}
      {groups.length === 0 && transactions.data && outbox && (
        <p className="text-text-muted">
          <FormattedMessage id="transactions.empty" />
        </p>
      )}
      {/* Paused offline without a cache, it would load forever; the offline status says why. */}
      {groups.length === 0 &&
        !transactions.data &&
        !transactions.isError &&
        transactions.fetchStatus !== 'paused' && (
          <p>
            <FormattedMessage id="common.loading" />
          </p>
        )}
      {groups.map((group) => (
        <section key={group.date}>
          <h2 id={`day-${group.date}`} className="text-sm font-semibold text-text-muted">
            {dayTitle(group.date)}
          </h2>
          <ul aria-labelledby={`day-${group.date}`} className="divide-y divide-text-muted/20">
            {group.items.map((item) => (
              <TransactionRow
                key={item.id}
                item={item}
                categories={categories}
                accounts={accounts}
                workspace={workspace}
                displayName={displayName}
              />
            ))}
          </ul>
        </section>
      ))}
    </>
  )
}
