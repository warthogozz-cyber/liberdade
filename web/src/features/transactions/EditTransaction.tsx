import { useEffect, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useNavigate } from 'react-router'
import {
  ApiError,
  useAccounts,
  useCategories,
  useTransactions,
  useUpdateTransaction,
  type MeWorkspace,
  type TransactionView,
  type UpdateTransactionRequest,
} from '@/shared/api'
import { currentMonth } from '@/shared/i18n'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { PageHeader, TransactionForm } from './TransactionForm'
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
 * Opens a saved transaction for editing: it is found in the month's list the page came from (there
 * is no read by id), and kept as opened, so a refresh behind a conflict does not change the form.
 * This module loads only for `?editar=`, so none of it weighs on recording.
 */
export function EditTransaction({ id, month, userId, workspace }: EditTransactionProps) {
  const navigate = useNavigate()
  const online = useOnline()
  const listMonth =
    month && /^\d{4}-(0[1-9]|1[0-2])$/.test(month)
      ? month
      : currentMonth(new Date(), workspace.timezone)
  const list = useTransactions(workspace.id, listMonth)
  const { refetch } = list
  // A cached month, even a fresh one, may lack what the server has: ask once for this visit.
  useEffect(() => {
    void refetch()
  }, [refetch])
  const [opened, setOpened] = useState<TransactionView>()
  const found = list.data?.find((each) => each.id === id)
  if (found && !opened) setOpened(found)

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
  // Only a fetch made for this visit can say the transaction is gone.
  const settled = list.isSuccess && list.isFetchedAfterMount && !list.isFetching
  return (
    <>
      <PageHeader kind="EXPENSE" editMonth={listMonth} />
      {settled ? (
        <DeletedElsewhereDialog
          open
          onBack={() => void navigate(`/lancamentos?mes=${listMonth}`)}
        />
      ) : (
        <p>
          <FormattedMessage id={list.isError ? 'common.error' : 'common.loading'} />
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
            void submit({ ...conflict.saved, version: conflict.current.version })
            setConflict(undefined)
          }}
        />
      )}
    </TransactionForm>
  )
}
