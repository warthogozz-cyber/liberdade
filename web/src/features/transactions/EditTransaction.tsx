import { useEffect, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import {
  ApiError,
  useAccounts,
  useCategories,
  queryKeys,
  useTransactions,
  useUpdateTransaction,
  type MeWorkspace,
  type TransactionView,
  type UpdateTransactionRequest,
} from '@/shared/api'
import { currentMonth } from '@/shared/i18n'
import { useTransaction } from '@/shared/api/detail'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { PageHeader, TransactionForm } from './TransactionForm'
import './more.register'
import { DeletedElsewhereDialog, VersionConflictDialog } from './VersionConflictDialog'

type ConflictState =
  | { kind: 'deleted' }
  | { kind: 'conflict'; saved: UpdateTransactionRequest; current: TransactionView }

interface EditTransactionProps {
  id: string
  month: string | null
  userId: string
  workspace: MeWorkspace
}

/**
 * Opens a saved transaction for editing from a fresh read by id, kept as opened so a refresh behind
 * a conflict does not change the form. Only when that read fails for a reason other than the
 * transaction being gone does the month's list stand in. This module loads only for `?editar=`, so
 * none of it weighs on recording.
 */
export function EditTransaction({ id, month, userId, workspace }: EditTransactionProps) {
  const queryClient = useQueryClient()
  const online = useOnline()
  const listMonth =
    month && /^\d{4}-(0[1-9]|1[0-2])$/.test(month)
      ? month
      : currentMonth(new Date(), workspace.timezone)
  const list = useTransactions(workspace.id, listMonth)
  const one = useTransaction(workspace.id, id)
  const [opened, setOpened] = useState<TransactionView>()
  const gone =
    one.error instanceof ApiError &&
    (one.error.status === 404 ||
      (one.error.status === 409 && one.error.code === 'transaction.deleted'))
      ? one.error
      : undefined
  const missing = gone?.status === 404
  useEffect(() => {
    // The list still shows what is gone: ask it again.
    if (missing) {
      void queryClient.invalidateQueries({
        queryKey: queryKeys.transactions(workspace.id, listMonth),
      })
    }
  }, [missing, queryClient, workspace.id, listMonth])
  const found = list.data?.find((each) => each.id === id)
  // The copy cached from an earlier visit shows first: wait for this visit's read before opening.
  const fresh = gone
    ? undefined
    : one.isError
      ? found
      : one.isFetchedAfterMount
        ? one.data
        : undefined
  if (fresh && !opened) setOpened(fresh)

  if (!online) {
    // Editing needs the connection (ADR-0007), so there is no form to fill in.
    return (
      <>
        <PageHeader kind="EXPENSE" editMonth={listMonth} />
        <p className="rounded-card bg-surface p-4">
          <FormattedMessage id="transactions.offlineEditing" />
        </p>
      </>
    )
  }
  if (opened) {
    return (
      <EditForm
        key={opened.id}
        userId={userId}
        workspace={workspace}
        opened={opened}
        month={listMonth}
      />
    )
  }
  return (
    <>
      <PageHeader kind="EXPENSE" editMonth={listMonth} />
      {gone ? (
        <div className="flex flex-col items-start gap-3 rounded-card bg-surface p-4">
          <p role="alert">
            <FormattedMessage id={missing ? 'transactions.goneNow' : 'transactions.deletedPlain'} />
          </p>
          <Link
            to={`/lancamentos?mes=${listMonth}`}
            className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="transactions.backToList" />
          </Link>
        </div>
      ) : (
        <p>
          <FormattedMessage id={one.isError ? 'common.error' : 'common.loading'} />
        </p>
      )}
    </>
  )
}

interface EditFormProps {
  userId: string
  workspace: MeWorkspace
  opened: TransactionView
  month: string
}

function EditForm({ userId, workspace, opened, month }: EditFormProps) {
  const intl = useIntl()
  const navigate = useNavigate()
  const toast = useToast()
  const update = useUpdateTransaction(workspace.id)
  const expenseCategories = useCategories(workspace.id, 'EXPENSE')
  const incomeCategories = useCategories(workspace.id, 'INCOME')
  const accounts = useAccounts(workspace.id)
  const [conflict, setConflict] = useState<ConflictState>()
  const [resending, setResending] = useState(false)
  const listPath = `/lancamentos?mes=${month}`

  /** Saves the edit; a conflict or a transaction gone elsewhere opens its dialog (D12). */
  async function submit(body: UpdateTransactionRequest): Promise<boolean> {
    try {
      await update.mutateAsync({ id: opened.id, body })
    } catch (error) {
      const current = error instanceof ApiError ? error.extra.current : undefined
      if (
        error instanceof ApiError &&
        error.status === 409 &&
        error.code === 'transaction.version-conflict' &&
        typeof current === 'object' &&
        current !== null
      ) {
        setConflict({ kind: 'conflict', saved: body, current: current as TransactionView })
      } else if (
        error instanceof ApiError &&
        (error.status === 404 || (error.status === 409 && error.code === 'transaction.deleted'))
      ) {
        setConflict({ kind: 'deleted' })
      } else {
        console.error(error)
        toast.show({ message: intl.formatMessage({ id: 'expense.saveFailed' }) })
      }
      return false
    }
    toast.show({ message: intl.formatMessage({ id: 'transactions.updated' }) })
    await navigate(listPath)
    return true
  }

  return (
    <TransactionForm
      userId={userId}
      workspace={workspace}
      fixing={undefined}
      initialKind={opened.kind}
      initialToAccountId={null}
      editing={opened}
      month={month}
      onEdit={submit}
      busy={resending}
    >
      {conflict?.kind === 'deleted' && (
        <DeletedElsewhereDialog open onBack={() => void navigate(listPath)} />
      )}
      {conflict?.kind === 'conflict' && (
        <VersionConflictDialog
          open
          saved={conflict.saved}
          current={conflict.current}
          accounts={new Map((accounts.data ?? []).map((each) => [each.id, each]))}
          categories={
            new Map(
              [...(expenseCategories.data ?? []), ...(incomeCategories.data ?? [])].map((each) => [
                each.id,
                each,
              ]),
            )
          }
          onKeepTheirs={() => void navigate(listPath)}
          onKeepMine={() => {
            setResending(true)
            void submit({ ...conflict.saved, version: conflict.current.version }).finally(() =>
              setResending(false),
            )
            setConflict(undefined)
          }}
        />
      )}
    </TransactionForm>
  )
}
