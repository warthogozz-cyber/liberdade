import { useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, Navigate } from 'react-router'
import {
  ApiError,
  useAccounts,
  useArchiveAccount,
  useCurrentWorkspace,
  useFormatMoney,
  useUnarchiveAccount,
  type AccountView,
  type MeWorkspace,
} from '@/shared/api'
import { parseCents } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Sheet } from '@/shared/ui/Sheet'
import { accountTypeInfo } from './accountTypes'
import { memberName } from '@/features/members/memberName'
import { useMembers } from '@/shared/api/family'
import { AccountForm } from './AccountForm'
import { AdjustBalanceSheet } from './AdjustBalanceSheet'
import './register'

type View = { kind: 'new' } | { kind: 'edit' | 'adjust' | 'archive'; id: string }

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const primary = `${button} bg-primary text-on-primary`
const outline = `${button} border border-primary text-primary`

/** The family's accounts: add, rename, adjust the balance, archive, and pay the card bill. */
export function AccountsPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'accounts.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  // A Youth's menu has no such page; one reached by its address goes back to the menu.
  if (workspace?.role === 'YOUTH') return <Navigate to="/mais" replace />

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {workspace ? (
        <AccountsScreen workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

/** The text of a failed call: the API's own, or the generic one when it had none. */
function failureText(failure: unknown, fallback: string): string {
  return (failure instanceof ApiError && failure.detail) || fallback
}

function AccountsScreen({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const format = useFormatMoney()
  const toast = useToast()
  const online = useOnline()
  const accounts = useAccounts(workspace.id)
  const archive = useArchiveAccount(workspace.id)
  const unarchive = useUnarchiveAccount(workspace.id)
  const members = useMembers(workspace.id).data
  const archiving = useRef(false)
  const [view, setView] = useState<View | null>(null)
  const [archiveError, setArchiveError] = useState<string>()
  const [unarchiveError, setUnarchiveError] = useState<string>()

  /** Who the account belongs to, once the family is loaded: "Titular: Leo". */
  function ownerLine(account: AccountView): string | undefined {
    const owner = members?.find((each) => each.id === account.ownerMembershipId)
    return owner
      ? intl.formatMessage({ id: 'accounts.owner.line' }, { name: memberName(owner, intl) })
      : undefined
  }

  const all = accounts.data ?? []
  const active = all.filter((account) => account.archivedAt === null)
  const archived = all.filter((account) => account.archivedAt !== null)
  const selected =
    view && view.kind !== 'new' ? all.find((account) => account.id === view.id) : undefined
  const generic = intl.formatMessage({ id: 'common.actionFailed' })

  function open(id: string) {
    setArchiveError(undefined)
    setView({ kind: 'edit', id })
  }

  async function confirmArchive(account: AccountView) {
    if (archiving.current || archive.isPending) return
    archiving.current = true
    try {
      await archive.mutateAsync(account.id)
    } catch (failure) {
      setArchiveError(failureText(failure, generic))
      setView({ kind: 'edit', id: account.id })
      return
    } finally {
      archiving.current = false
    }
    toast.show({ message: intl.formatMessage({ id: 'accounts.archivedToast' }) })
    setView(null)
  }

  async function reactivate(account: AccountView) {
    if (unarchive.isPending) return
    setUnarchiveError(undefined)
    try {
      await unarchive.mutateAsync(account.id)
    } catch (failure) {
      setUnarchiveError(failureText(failure, generic))
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'accounts.unarchivedToast' }) })
  }

  return (
    <>
      <button
        type="button"
        disabled={!online}
        onClick={() => setView({ kind: 'new' })}
        className={primary}
      >
        <FormattedMessage id="accounts.new" />
      </button>
      {!online && (
        <p role="status" className="text-text-muted">
          <FormattedMessage id="accounts.offline" />
        </p>
      )}
      {accounts.isPending ? (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      ) : accounts.isError && all.length === 0 ? (
        <div className="flex flex-col items-start gap-2">
          <p>
            <FormattedMessage id="common.error" />
          </p>
          <button type="button" onClick={() => void accounts.refetch()} className={outline}>
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {active.map((account) => {
            const info = accountTypeInfo(account.type)
            return (
              <li key={account.id}>
                <button
                  type="button"
                  onClick={() => open(account.id)}
                  className="flex min-h-14 w-full items-center gap-3 rounded-card bg-surface p-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <span aria-hidden="true" className="text-2xl">
                    {info.icon}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate font-medium">{account.name}</span>
                    <span className="text-text-muted">
                      {intl.formatMessage({ id: info.labelId })}
                    </span>
                    {ownerLine(account) && (
                      <span className="truncate text-sm text-text-muted">{ownerLine(account)}</span>
                    )}
                  </span>
                  <span className="font-medium tabular-nums">
                    {format(parseCents(account.balance.amount))}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
      {archived.length > 0 && (
        <details className="rounded-card bg-surface p-3">
          <summary className="flex min-h-12 cursor-pointer items-center font-medium">
            {intl.formatMessage({ id: 'accounts.archived' }, { count: archived.length })}
          </summary>
          <ul className="mt-2 flex flex-col gap-2">
            {archived.map((account) => {
              const info = accountTypeInfo(account.type)
              return (
                <li key={account.id} className="flex items-center gap-3">
                  <span aria-hidden="true" className="text-2xl">
                    {info.icon}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate font-medium">{account.name}</span>
                    <span className="text-text-muted">
                      {intl.formatMessage({ id: info.labelId })}
                    </span>
                  </span>
                  <button
                    type="button"
                    disabled={!online || unarchive.isPending}
                    onClick={() => void reactivate(account)}
                    className={outline}
                  >
                    <FormattedMessage id="accounts.unarchive" />
                  </button>
                </li>
              )
            })}
          </ul>
          {unarchiveError && (
            <p role="alert" className="mt-2 text-negative">
              {unarchiveError}
            </p>
          )}
        </details>
      )}

      <Sheet
        open={view?.kind === 'new'}
        onOpenChange={(next) => {
          if (!next) setView(null)
        }}
        title={intl.formatMessage({ id: 'accounts.new' })}
      >
        <AccountForm workspace={workspace} onDone={() => setView(null)} />
      </Sheet>
      {selected && (
        <>
          <Sheet
            open={view?.kind === 'edit'}
            onOpenChange={(next) => {
              if (!next) setView(null)
            }}
            title={selected.name}
          >
            <EditActions
              key={selected.id}
              workspace={workspace}
              account={selected}
              online={online}
              error={archiveError}
              onDone={() => setView(null)}
              onAdjust={() => setView({ kind: 'adjust', id: selected.id })}
              onArchive={() => setView({ kind: 'archive', id: selected.id })}
            />
          </Sheet>
          {view?.kind === 'adjust' && (
            <AdjustBalanceSheet
              workspace={workspace}
              account={selected}
              onClose={() => setView(null)}
            />
          )}
          <ConfirmDialog
            open={view?.kind === 'archive'}
            title={intl.formatMessage({ id: 'accounts.archive.title' }, { name: selected.name })}
            body={archiveBody(intl.formatMessage, format, selected)}
            confirmLabel={<FormattedMessage id="accounts.archive" />}
            cancelLabel={<FormattedMessage id="accounts.cancel" />}
            confirmDisabled={archive.isPending}
            onConfirm={() => void confirmArchive(selected)}
            onCancel={() => {
              setArchiveError(undefined)
              setView({ kind: 'edit', id: selected.id })
            }}
          />
        </>
      )}
    </>
  )
}

/** The archive question's body: the balance that leaves the family's total, when there is one. */
function archiveBody(
  formatMessage: ReturnType<typeof useIntl>['formatMessage'],
  format: (cents: bigint) => string,
  account: AccountView,
) {
  const balance = parseCents(account.balance.amount)
  return balance === 0n
    ? formatMessage({ id: 'accounts.archive.body' })
    : formatMessage({ id: 'accounts.archive.bodyWithBalance' }, { amount: format(balance) })
}

interface EditActionsProps {
  workspace: MeWorkspace
  account: AccountView
  online: boolean
  /** Why the last archive failed, when it did. */
  error: string | undefined
  onDone: () => void
  onAdjust: () => void
  onArchive: () => void
}

/**
 * What an account's sheet offers: name and type, Acertar saldo, Arquivar and, on a card, Pagar fatura.
 * An investment's balance is kept by hand, so its Acertar saldo comes first and stands out (D5).
 */
function EditActions({
  workspace,
  account,
  online,
  error,
  onDone,
  onAdjust,
  onArchive,
}: EditActionsProps) {
  const adjustFirst = account.type === 'INVESTMENT'
  const adjust = (
    <button
      type="button"
      disabled={!online}
      onClick={onAdjust}
      className={adjustFirst ? primary : outline}
    >
      <FormattedMessage id="accounts.adjust" />
    </button>
  )
  return (
    <div className="flex flex-col gap-4">
      {adjustFirst && adjust}
      <AccountForm workspace={workspace} account={account} onDone={onDone} />
      <div className="flex flex-col gap-2">
        {!adjustFirst && adjust}
        {account.type === 'CREDIT_CARD' && (
          <Link to={`/lancar?tipo=transferencia&para=${account.id}`} className={outline}>
            <FormattedMessage id="accounts.payBill" />
          </Link>
        )}
        <button
          type="button"
          disabled={!online}
          onClick={onArchive}
          className={`${button} border border-negative text-negative`}
        >
          <FormattedMessage id="accounts.archive" />
        </button>
      </div>
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
    </div>
  )
}
