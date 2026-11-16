import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace, useDashboard, useMe, type MeWorkspace } from '@/shared/api'
import { currentMonth } from '@/shared/i18n'
import { formatBRL, parseCents } from '@/shared/money'
import { useOnline, useOutbox, usePendingTotals } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'

const FIRST_STEPS_KEY = 'liberdade:first-steps-dismissed'

function readDismissed(): boolean {
  try {
    return localStorage.getItem(FIRST_STEPS_KEY) === '1'
  } catch {
    return false
  }
}

/** The home screen: the month's totals with the user's queue in them (D25, wireframe 2). */
export function HomePage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'nav.home' }))
  const me = useMe().data
  const workspace = useCurrentWorkspace()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.home" />
      </h1>
      {me && workspace ? (
        <HomeContent
          userId={me.user.id}
          hasPasskey={me.user.passkeyCreatedAt != null}
          workspace={workspace}
        />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

interface HomeContentProps {
  userId: string
  hasPasskey: boolean
  workspace: MeWorkspace
}

function HomeContent({ userId, hasPasskey, workspace }: HomeContentProps) {
  const intl = useIntl()
  const online = useOnline()
  const month = currentMonth(new Date(), workspace.timezone)
  const dashboard = useDashboard(workspace.id, month)
  const pending = usePendingTotals(userId, workspace.id, month)
  const attention = (useOutbox(userId, workspace.id) ?? []).filter(
    (item) => item.status === 'needs-attention',
  ).length
  const [dismissed, setDismissed] = useState(readDismissed)
  const monthName = intl.formatDate(`${month}-01T00:00:00Z`, { month: 'long', timeZone: 'UTC' })

  function dismiss() {
    setDismissed(true)
    try {
      localStorage.setItem(FIRST_STEPS_KEY, '1')
    } catch {
      // Without storage the card comes back next time.
    }
  }

  let totals
  if (dashboard.data) {
    const expenses = parseCents(dashboard.data.expenses.amount) + pending.monthExpenses
    const balance = parseCents(dashboard.data.balance.amount) - pending.allExpenses
    totals = (
      <dl className="grid gap-4 rounded-card bg-surface p-4">
        <div>
          <dt className="text-sm text-text-muted">
            <FormattedMessage id="home.expensesIn" values={{ month: monthName }} />
          </dt>
          <dd className="text-3xl font-semibold tabular-nums">{formatBRL(expenses)}</dd>
        </div>
        <div>
          <dt className="text-sm text-text-muted">
            <FormattedMessage id="home.totalBalance" />
          </dt>
          <dd className="text-xl font-semibold tabular-nums">{formatBRL(balance)}</dd>
        </div>
      </dl>
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
      {totals}
      <p className="text-sm text-text-muted">
        <FormattedMessage id="home.addAccountsHint" />
      </p>
      {attention > 0 && (
        <Link
          to="/lancamentos"
          className="flex min-h-12 items-center rounded-card bg-surface px-4 font-medium text-negative focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="home.needsAttention" values={{ count: attention }} />
        </Link>
      )}
      {!dismissed && !hasPasskey && (
        <section aria-labelledby="first-steps" className="rounded-card bg-surface p-4">
          <div className="flex items-start justify-between gap-2">
            <h2 id="first-steps" className="font-semibold">
              <FormattedMessage id="home.firstSteps" />
            </h2>
            <button
              type="button"
              onClick={dismiss}
              aria-label={intl.formatMessage({ id: 'home.dismissFirstSteps' })}
              className="flex size-12 items-center justify-center rounded-field focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <span aria-hidden="true">✕</span>
            </button>
          </div>
          <p>
            <FormattedMessage id="home.passkeyStep" />
          </p>
          <Link
            to="/mais"
            className="mt-2 inline-flex min-h-12 items-center font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="home.passkeyStepLink" />
          </Link>
        </section>
      )}
    </>
  )
}
