import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import {
  useAccounts,
  useDismissGettingStarted,
  useMe,
  type MeView,
  type MeWorkspace,
} from '@/shared/api'
import { useOnline, useOutbox } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { ACCOUNTS_DONE_AT, shouldShowGettingStarted } from './gettingStarted'
import './gettingStarted.register'

interface GettingStartedCardProps {
  workspace: MeWorkspace
  me: MeView
}

const LINK_CLASS =
  'inline-flex min-h-12 items-center font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The first steps of a new family: add the accounts, create a passkey. Each member closes it once (D15). */
export function GettingStartedCard({ workspace, me }: GettingStartedCardProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  // Closing updates the cached `me`, so the card follows it rather than the props it was given.
  const current = useMe().data?.workspaces.find(({ id }) => id === workspace.id) ?? workspace
  const accounts = useAccounts(workspace.id).data
  const queue = useOutbox(me.user.id, workspace.id)
  const dismiss = useDismissGettingStarted({
    onError: () =>
      toast.show({ message: intl.formatMessage({ id: 'gettingStarted.dismissFailed' }) }),
  })

  // The accounts arrive after the card does; with none yet, a family that is done would flash it.
  if (!accounts) return null
  const accountCount = accounts.length
  const hasPasskey = me.user.passkeyCreatedAt != null
  if (
    !shouldShowGettingStarted({
      hasTransactions: current.hasTransactions,
      queued: (queue?.length ?? 0) > 0,
      dismissedAt: current.gettingStartedDismissedAt,
      accountCount,
      hasPasskey,
    })
  ) {
    return null
  }

  const offline = intl.formatMessage({ id: 'gettingStarted.offline' })
  const steps = [
    {
      id: 'accounts',
      label: intl.formatMessage({ id: 'gettingStarted.accounts' }),
      to: '/mais/contas',
      done: accountCount >= ACCOUNTS_DONE_AT,
    },
    {
      id: 'passkey',
      label: intl.formatMessage({ id: 'gettingStarted.passkey' }),
      to: '/mais',
      done: hasPasskey,
    },
  ]

  return (
    <section aria-labelledby="getting-started" className="rounded-card bg-surface p-4">
      <div className="flex items-start justify-between gap-2">
        <h2 id="getting-started" className="font-semibold">
          <FormattedMessage id="gettingStarted.title" />
        </h2>
        <button
          type="button"
          disabled={!online}
          title={online ? undefined : offline}
          onClick={() => dismiss.mutate(workspace.id)}
          aria-label={intl.formatMessage({ id: 'gettingStarted.close' })}
          className="flex size-12 items-center justify-center rounded-field focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
        >
          <span aria-hidden="true">✕</span>
        </button>
      </div>
      <ul className="flex flex-col">
        {steps.map((step) => (
          <li key={step.id} className="flex min-h-12 items-center justify-between gap-2">
            {step.done ? (
              <>
                <span>{step.label}</span>
                <span className="font-medium text-positive">
                  <span aria-hidden="true">✓ </span>
                  <FormattedMessage id="gettingStarted.done" />
                </span>
              </>
            ) : (
              <Link to={step.to} className={LINK_CLASS}>
                {step.label}
              </Link>
            )}
          </li>
        ))}
      </ul>
      {!online && <p className="text-sm text-text-muted">{offline}</p>}
    </section>
  )
}
