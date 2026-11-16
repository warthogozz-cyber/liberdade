import { useMemo, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useSearchParams } from 'react-router'
import {
  useAccounts,
  useCategories,
  useCurrentWorkspace,
  useDeleteTransaction,
  useMe,
  useRestoreTransaction,
  useTransactions,
  type AccountView,
  type CategoryView,
  type MeWorkspace,
  type TransactionFilters as Filters,
} from '@/shared/api'
import { MIN_MONTH, addDays, currentMonth, localDate, maxMonth } from '@/shared/i18n'
import { useOnline, useOutbox, type OutboxItem } from '@/shared/offline'
import { DataAsOf, MonthSwitcher, useDocumentTitle, useToast } from '@/shared/ui'
import { groupByDay, mergeTransactions } from './listItems'
import { TransactionFilters } from './TransactionFilters'
import { TransactionRow } from './TransactionRow'

const MONTH_FORMAT = /^\d{4}-(0[1-9]|1[0-2])$/

const outlineButton =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The month's transactions with the user's queue, by day (wireframe 6); the URL keeps month and filters. */
export function TransactionsPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'nav.transactions' })
  useDocumentTitle(title)
  const user = useMe().data?.user
  const workspace = useCurrentWorkspace()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {user && workspace ? (
        <TransactionsScreen userId={user.id} displayName={user.displayName} workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

interface ScreenProps {
  userId: string
  displayName: string
  workspace: MeWorkspace
}

function TransactionsScreen({ userId, displayName, workspace }: ScreenProps) {
  const online = useOnline()
  const [params, setParams] = useSearchParams()
  const now = new Date()
  const thisMonth = currentMonth(now, workspace.timezone)
  const lastMonth = maxMonth(now, workspace.timezone)
  const asked = params.get('mes')
  const month =
    asked && MONTH_FORMAT.test(asked) && asked >= MIN_MONTH && asked <= lastMonth
      ? asked
      : thisMonth
  const categoryId = params.get('categoria') ?? undefined
  const accountId = params.get('conta') ?? undefined
  const filters = useMemo<Filters>(
    () => ({ ...(categoryId && { categoryId }), ...(accountId && { accountId }) }),
    [categoryId, accountId],
  )
  const [filtering, setFiltering] = useState(false)
  const transactions = useTransactions(workspace.id, month, filters)
  const expenseCategories = useCategories(workspace.id, 'EXPENSE').data
  const incomeCategories = useCategories(workspace.id, 'INCOME').data
  const accountList = useAccounts(workspace.id).data
  const categoryList = useMemo(
    () => [...(expenseCategories ?? []), ...(incomeCategories ?? [])],
    [expenseCategories, incomeCategories],
  )

  function update(next: { month?: string; categoryId?: string | null; accountId?: string | null }) {
    const merged = new URLSearchParams(params)
    const set = (key: string, value: string | null | undefined) => {
      if (value === undefined) return
      if (value === null) merged.delete(key)
      else merged.set(key, value)
    }
    set('mes', next.month)
    set('categoria', next.categoryId)
    set('conta', next.accountId)
    setParams(merged)
  }

  return (
    <>
      {!online && (
        <div role="status" className="rounded-card bg-surface p-4">
          <p>
            <FormattedMessage id="transactions.offline" />
          </p>
          <p className="mt-1">
            <FormattedMessage id="transactions.offlineEditing" />
          </p>
          {transactions.data && (
            <p className="mt-1 text-sm text-text-muted">
              <DataAsOf updatedAt={transactions.dataUpdatedAt} timeZone={workspace.timezone} />
            </p>
          )}
        </div>
      )}
      <MonthSwitcher month={month} max={lastMonth} onChange={(next) => update({ month: next })} />
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" onClick={() => setFiltering(true)} className={outlineButton}>
          <FormattedMessage id="transactions.filters" />
        </button>
        {categoryId && (
          <FilterChip
            label={categoryNameOf(categoryList, categoryId)}
            onClear={() => update({ categoryId: null })}
          />
        )}
        {accountId && (
          <FilterChip
            label={accountList?.find(({ id }) => id === accountId)?.name ?? ''}
            onClear={() => update({ accountId: null })}
          />
        )}
      </div>
      <TransactionFilters
        open={filtering}
        onOpenChange={setFiltering}
        filters={filters}
        onChange={(next) => {
          update({ categoryId: next.categoryId ?? null, accountId: next.accountId ?? null })
          setFiltering(false)
        }}
        categories={categoryList}
        accounts={accountList ?? []}
      />
      <TransactionList
        userId={userId}
        displayName={displayName}
        workspace={workspace}
        month={month}
        filters={filters}
        online={online}
        categoryList={categoryList}
        accountList={accountList}
        transactions={transactions}
      />
    </>
  )
}

function categoryNameOf(categories: CategoryView[], id: string): string {
  return categories.find((each) => each.id === id)?.name ?? ''
}

function FilterChip({ label, onClear }: { label: string; onClear: () => void }) {
  const intl = useIntl()
  const clear = intl.formatMessage({ id: 'transactions.filter.clear' })
  return (
    <span className="flex items-center gap-1 rounded-field bg-surface pl-3">
      {label}
      <button
        type="button"
        aria-label={`${clear} ${label}`}
        onClick={onClear}
        className="min-h-12 rounded-field px-2 font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        {clear}
      </button>
    </span>
  )
}

interface TransactionListProps {
  userId: string
  displayName: string
  workspace: MeWorkspace
  month: string
  filters: Filters
  online: boolean
  categoryList: CategoryView[]
  accountList: AccountView[] | undefined
  transactions: ReturnType<typeof useTransactions>
}

/**
 * Whether a queued item belongs in the view. Without a filter the whole queue shows, of any month,
 * so no refused item can hide (Plan 2); with one, the queue follows the filters like the API's list.
 */
function queuedBelongs(
  { payload }: OutboxItem,
  filters: Filters,
  categoryList: CategoryView[],
): boolean {
  const { accountId, categoryId } = filters
  if (accountId && payload.accountId !== accountId && payload.toAccountId !== accountId)
    return false
  if (categoryId) {
    const wanted = new Set([
      categoryId,
      ...categoryList.filter(({ parentId }) => parentId === categoryId).map(({ id }) => id),
    ])
    if (!payload.categoryId || !wanted.has(payload.categoryId)) return false
  }
  return true
}

function TransactionList({
  userId,
  displayName,
  workspace,
  month,
  filters,
  online,
  categoryList,
  accountList,
  transactions,
}: TransactionListProps) {
  const intl = useIntl()
  const toast = useToast()
  const today = localDate(new Date(), workspace.timezone)
  const yesterday = addDays(today, -1)
  const outbox = useOutbox(userId, workspace.id)
  const remove = useDeleteTransaction(workspace.id)
  const restore = useRestoreTransaction(workspace.id)
  const categories = useMemo(
    () => new Map(categoryList.map((each) => [each.id, each])),
    [categoryList],
  )
  const accounts = useMemo(
    () => new Map((accountList ?? []).map((each) => [each.id, each])),
    [accountList],
  )
  const queued = (outbox ?? []).filter((item) => queuedBelongs(item, filters, categoryList))
  const groups = groupByDay(mergeTransactions(transactions.data, queued))

  function failed(error: unknown) {
    console.error(error)
    toast.show({ message: intl.formatMessage({ id: 'common.actionFailed' }) })
  }

  async function undo(id: string) {
    if (!navigator.onLine) {
      toast.show({ message: intl.formatMessage({ id: 'transactions.restoreOffline' }) })
      return
    }
    await restore.mutateAsync(id)
    toast.show({ message: intl.formatMessage({ id: 'transactions.restored' }) })
  }

  async function onDelete(id: string) {
    try {
      await remove.mutateAsync(id)
    } catch (error) {
      failed(error)
      return
    }
    toast.show({
      message: intl.formatMessage({ id: 'transactions.deleted' }),
      action: {
        label: intl.formatMessage({ id: 'transaction.undo' }),
        altText: intl.formatMessage({ id: 'transaction.undoAlt' }),
        onClick: () => {
          undo(id).catch(failed)
        },
      },
    })
  }

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
            className={outlineButton}
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
                month={month}
                online={online}
                onDelete={(id) => void onDelete(id)}
              />
            ))}
          </ul>
        </section>
      ))}
    </>
  )
}
