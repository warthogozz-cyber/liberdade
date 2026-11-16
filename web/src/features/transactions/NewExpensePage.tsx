import { useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { v7 } from 'uuid'
import {
  useAccounts,
  useCategories,
  useCurrentWorkspace,
  useMe,
  type CreateTransactionRequest,
  type MeWorkspace,
} from '@/shared/api'
import { addDays, localDate } from '@/shared/i18n'
import { centsToApi, formatBRL, parseCents } from '@/shared/money'
import {
  cancelOutboxItem,
  enqueueTransaction,
  replaceOutboxItem,
  useOnline,
  useOutbox,
  type OutboxItem,
} from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { CategoryPicker } from './CategoryPicker'
import { ContextSheet, type ExpenseDate } from './ContextSheet'
import { Keypad } from './Keypad'

const lastAccountKey = (workspaceId: string) => `liberdade:last-account:${workspaceId}`

function readLastAccount(workspaceId: string): string | null {
  try {
    return localStorage.getItem(lastAccountKey(workspaceId))
  } catch {
    return null
  }
}

function writeLastAccount(workspaceId: string, accountId: string): void {
  try {
    localStorage.setItem(lastAccountKey(workspaceId), accountId)
  } catch {
    // Without storage the first account is offered next time.
  }
}

/** The new expense, full screen (wireframe 3). With `?corrigir=<id>`, it fixes a refused one. */
export function NewExpensePage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'nav.newExpense' })
  useDocumentTitle(title)
  const userId = useMe().data?.user.id
  const workspace = useCurrentWorkspace()
  const fixId = useSearchParams()[0].get('corrigir')
  const outbox = useOutbox(userId, workspace?.id)
  const fixing = outbox?.find((item) => item.id === fixId && item.status === 'needs-attention')
  // A fix waits for the queue, so the form opens filled in rather than filling in later.
  const ready = userId !== undefined && workspace && (fixId === null || outbox !== undefined)

  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col gap-4 p-4">
      <header className="flex items-center gap-2">
        <Link
          to="/inicio"
          aria-label={intl.formatMessage({ id: 'expense.back' })}
          className="flex size-12 items-center justify-center rounded-field text-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <span aria-hidden="true">←</span>
        </Link>
        <h1 className="text-xl font-semibold">{title}</h1>
      </header>
      {ready ? (
        <ExpenseForm
          key={fixing?.id ?? 'new'}
          userId={userId}
          workspace={workspace}
          fixing={fixing}
        />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

interface ExpenseFormProps {
  userId: string
  workspace: MeWorkspace
  /** The refused item being fixed, which fills the form in. */
  fixing: OutboxItem | undefined
}

function ExpenseForm({ userId, workspace, fixing }: ExpenseFormProps) {
  const intl = useIntl()
  const navigate = useNavigate()
  const toast = useToast()
  const online = useOnline()
  const categories = useCategories(workspace.id, 'EXPENSE')
  const accounts = useAccounts(workspace.id)
  const [cents, setCents] = useState(() => (fixing ? parseCents(fixing.payload.amount.amount) : 0n))
  const [categoryId, setCategoryId] = useState(() => fixing?.payload.categoryId ?? undefined)
  const [date, setDate] = useState<ExpenseDate>(
    // The API sends dates as YYYY-MM-DD.
    () => (fixing?.payload.occurredOn as ExpenseDate | undefined) ?? 'today',
  )
  const [chosenAccountId, setChosenAccountId] = useState(
    () => fixing?.payload.accountId ?? readLastAccount(workspace.id),
  )
  const [description, setDescription] = useState(() => fixing?.payload.description ?? '')
  const [sheet, setSheet] = useState<'categories' | 'context' | null>(null)
  const [saving, setSaving] = useState(false)
  // State lags a render behind; the ref stops a double tap from queueing twice (Review Focus 1).
  const savingRef = useRef(false)

  const accountList = accounts.data ?? []
  const account = accountList.find(({ id }) => id === chosenAccountId) ?? accountList.at(0)
  const categoryList = categories.data ?? []
  // Without a cache, only being offline means the categories cannot come; an online error retries.
  const noCategories = categories.data
    ? categories.data.length === 0
    : !online || categories.fetchStatus === 'paused'
  const categoryError = !categories.data && !noCategories && categories.isError
  // A fix can carry a category that is archived or not cached: it has no chip, so it cannot go.
  const categoryKnown = categoryList.some(({ id }) => id === categoryId)
  const canSave = cents > 0n && !noCategories && categoryKnown && account !== undefined && !saving
  const today = localDate(new Date(), workspace.timezone)
  const dateLabel =
    date === 'today' || date === 'yesterday'
      ? intl.formatMessage({ id: date === 'today' ? 'common.today' : 'common.yesterday' })
      : intl.formatDate(`${date}T00:00:00Z`, { day: 'numeric', month: 'short', timeZone: 'UTC' })

  async function undo(id: string) {
    const cancelled = await cancelOutboxItem(id)
    toast.show({
      message: intl.formatMessage({ id: cancelled ? 'expense.undone' : 'expense.undoTooLate' }),
    })
  }

  function undoFailed(error: unknown) {
    console.error(error)
    toast.show({ message: intl.formatMessage({ id: 'common.actionFailed' }) })
  }

  async function save() {
    if (!canSave || !account || categoryId === undefined || savingRef.current) return
    savingRef.current = true
    setSaving(true)
    // Hoje and Ontem become dates now, in the family's time zone (Review Focus 4).
    const savedToday = localDate(new Date(), workspace.timezone)
    const occurredOn =
      date === 'today' ? savedToday : date === 'yesterday' ? addDays(savedToday, -1) : date
    const payload: CreateTransactionRequest = {
      id: v7(),
      kind: 'EXPENSE',
      amount: { amount: centsToApi(cents), currency: workspace.currency },
      occurredOn,
      accountId: account.id,
      categoryId,
      description: description.trim() || null,
    }
    try {
      if (fixing) await replaceOutboxItem(fixing.id, payload)
      else await enqueueTransaction({ userId, workspaceId: workspace.id, payload })
    } catch (error) {
      // Nothing was queued, so Salvar can try again (Ruling T11-save-failed).
      console.error(error)
      savingRef.current = false
      setSaving(false)
      toast.show({ message: intl.formatMessage({ id: 'expense.saveFailed' }) })
      return
    }
    // The expense is queued: the guard stays up, so nothing after this can queue it twice.
    writeLastAccount(workspace.id, account.id)
    toast.show({
      message: intl.formatMessage({ id: 'expense.saved' }),
      // Undoing a fix would delete the refused expense too (Ruling T11-correction-undo).
      action: fixing
        ? undefined
        : {
            label: intl.formatMessage({ id: 'expense.undo' }),
            altText: intl.formatMessage({ id: 'expense.undoAlt' }),
            onClick: () => {
              undo(payload.id).catch(undoFailed)
            },
          },
    })
    await navigate('/inicio')
  }

  return (
    <div className="flex flex-1 flex-col gap-4">
      <output
        aria-label={intl.formatMessage({ id: 'expense.amount' })}
        aria-live="polite"
        className="block py-4 text-center text-4xl font-semibold tabular-nums"
      >
        {formatBRL(cents)}
      </output>
      {noCategories ? (
        <p className="rounded-card bg-surface p-4">
          <FormattedMessage id="expense.noCategories" />
        </p>
      ) : categoryError ? (
        <div className="flex flex-col items-start gap-2">
          <p>
            <FormattedMessage id="common.error" />
          </p>
          <button
            type="button"
            onClick={() => void categories.refetch()}
            className="min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : (
        <CategoryPicker
          categories={categoryList}
          value={categoryId}
          onChange={setCategoryId}
          onSheetChange={(open) => setSheet(open ? 'categories' : null)}
        />
      )}
      <button
        type="button"
        onClick={() => setSheet('context')}
        className="flex min-h-12 items-center justify-between rounded-field px-3 text-left text-text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage
          id="expense.context"
          values={{ date: dateLabel, account: account?.name ?? '' }}
        />
        <span aria-hidden="true">✎</span>
      </button>
      <ContextSheet
        open={sheet === 'context'}
        onOpenChange={(open) => setSheet(open ? 'context' : null)}
        date={date}
        onDateChange={setDate}
        today={today}
        accounts={accountList}
        accountId={account?.id}
        onAccountChange={setChosenAccountId}
      />
      <input
        type="text"
        aria-label={intl.formatMessage({ id: 'expense.description' })}
        placeholder={intl.formatMessage({ id: 'expense.description' })}
        maxLength={140}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        className="min-h-12 rounded-field border border-text-muted/30 bg-surface px-3"
      />
      <Keypad onChange={setCents} keyboard={sheet === null} />
      <button
        type="button"
        disabled={!canSave}
        onClick={() => void save()}
        className="min-h-14 rounded-field bg-primary text-lg font-semibold text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        <FormattedMessage id="expense.save" />
      </button>
    </div>
  )
}
