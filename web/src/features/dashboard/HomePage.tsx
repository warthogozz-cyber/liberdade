import { lazy, Suspense, useEffect, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import {
  useAccounts,
  useCurrentWorkspace,
  useDashboard,
  useFormatMoney,
  useMe,
  type MeView,
  type MeWorkspace,
} from '@/shared/api'
import { currentMonth } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import { ModuleSlot } from '@/shared/modules'
import { useOnline, useOutbox, usePendingTotals } from '@/shared/offline'
import { DataAsOf, useDocumentTitle } from '@/shared/ui'
import { isRecoveryNoticeRecent } from '@/features/members/recoveryWindow'
import { shouldShowGettingStarted } from './gettingStarted'
import './register'

// The old card kept its dismissal here. The new one lives on the server, so the key is only cleared.
const LEGACY_FIRST_STEPS_KEY = 'liberdade:first-steps-dismissed'

const GettingStartedCard = lazy(() =>
  import('./GettingStartedCard').then((m) => ({ default: m.GettingStartedCard })),
)

const RecoveryNotice = lazy(() =>
  import('@/features/members/RecoveryNotice').then((m) => ({ default: m.RecoveryNotice })),
)

/** The home screen: the month's totals with the user's queue in them (D25, wireframe 4). */
export function HomePage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'nav.home' }))
  const me = useMe().data
  const workspace = useCurrentWorkspace()

  useEffect(() => {
    try {
      localStorage.removeItem(LEGACY_FIRST_STEPS_KEY)
    } catch {
      // Without storage there is nothing to clear.
    }
  }, [])

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.home" />
      </h1>
      {me && workspace ? (
        <HomeContent me={me} workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

interface HomeContentProps {
  me: MeView
  workspace: MeWorkspace
}

function HomeContent({ me, workspace }: HomeContentProps) {
  const userId = me.user.id
  // When Home opened: the notice's week does not tick over while the screen is up.
  const [openedAt] = useState(Date.now)
  const intl = useIntl()
  const format = useFormatMoney()
  const online = useOnline()
  const month = currentMonth(new Date(), workspace.timezone)
  const dashboard = useDashboard(workspace.id, month)
  const pending = usePendingTotals(userId, workspace.id, month)
  const queue = useOutbox(userId, workspace.id) ?? []
  const attention = queue.filter((item) => item.status === 'needs-attention').length
  const accounts = useAccounts(workspace.id).data
  const youth = workspace.role === 'YOUTH'
  const monthName = intl.formatDate(`${month}-01T00:00:00Z`, { month: 'long', timeZone: 'UTC' })

  // The card needs the accounts to know whether it is done; until they load, its code stays unloaded.
  const showGettingStarted =
    accounts !== undefined &&
    shouldShowGettingStarted({
      hasTransactions: workspace.hasTransactions,
      queued: queue.length > 0,
      dismissedAt: workspace.gettingStartedDismissedAt,
      accountCount: accounts.length,
      hasPasskey: me.user.passkeyCreatedAt != null,
      role: workspace.role,
      familyInvited: workspace.familyInvited,
    })

  let totals
  if (dashboard.data) {
    const expenses = parseCents(dashboard.data.expenses.amount) + pending.monthExpenses
    // An income queued adds to the balance and an expense takes from it; a transfer changes nothing.
    const balance = parseCents(dashboard.data.balance.amount) + pending.balanceDelta
    totals = (
      <>
        <dl className="grid gap-4 rounded-card bg-surface p-4">
          <div>
            <dt className="text-sm text-text-muted">
              <FormattedMessage id="home.expensesIn" values={{ month: monthName }} />
            </dt>
            <dd className="text-3xl font-semibold tabular-nums">{format(expenses)}</dd>
          </div>
          <div>
            <dt className="text-sm text-text-muted">
              <FormattedMessage id={youth ? 'dashboard.yourWallet' : 'home.totalBalance'} />
            </dt>
            <dd className="text-xl font-semibold tabular-nums">{format(balance)}</dd>
          </div>
        </dl>
        {youth && accounts && accounts.length > 0 && (
          <ul className="flex flex-col gap-2 rounded-card bg-surface p-4">
            {accounts
              .filter((account) => account.archivedAt === null)
              .map((account) => (
                <li key={account.id} className="flex items-baseline justify-between gap-4">
                  <span className="truncate">{account.name}</span>
                  <span className="font-medium tabular-nums">
                    {format(parseCents(account.balance.amount))}
                  </span>
                </li>
              ))}
          </ul>
        )}
        <Link
          to="/painel"
          className="flex min-h-12 items-center font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="home.openDashboard" />
        </Link>
        {!online && (
          <p className="text-sm text-text-muted">
            <DataAsOf updatedAt={dashboard.dataUpdatedAt} timeZone={workspace.timezone} />
          </p>
        )}
      </>
    )
  } else if (!online || dashboard.fetchStatus === 'paused') {
    totals = (
      <p className="rounded-card bg-surface p-4">
        <FormattedMessage id="home.offlineNoData" />
      </p>
    )
  } else if (dashboard.isError) {
    totals = (
      <div className="flex flex-col items-start gap-2">
        <p>
          <FormattedMessage id="common.error" />
        </p>
        <button
          type="button"
          onClick={() => void dashboard.refetch()}
          className="min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="common.retry" />
        </button>
      </div>
    )
  } else {
    totals = (
      <p>
        <FormattedMessage id="common.loading" />
      </p>
    )
  }

  return (
    <>
      {isRecoveryNoticeRecent(workspace.recoveredAt, openedAt) && (
        <Suspense fallback={null}>
          <RecoveryNotice workspace={workspace} />
        </Suspense>
      )}
      {totals}
      <ModuleSlot name="home" />
      {!youth && (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="home.addAccountsHint" />
        </p>
      )}
      {attention > 0 && (
        <Link
          to="/lancamentos"
          className="flex min-h-12 items-center rounded-card bg-surface px-4 font-medium text-negative focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="home.needsAttention" values={{ count: attention }} />
        </Link>
      )}
      {showGettingStarted && (
        <Suspense fallback={null}>
          <GettingStartedCard workspace={workspace} me={me} />
        </Suspense>
      )}
    </>
  )
}
