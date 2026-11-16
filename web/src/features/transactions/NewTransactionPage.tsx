import { useId, useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { v7 } from 'uuid'
import { useQueryClient } from '@tanstack/react-query'
import {
  deleteTransaction,
  queryKeys,
  useAccounts,
  useCategories,
  useCurrentWorkspace,
  useMe,
  type AccountView,
  type CreateTransactionRequest,
  type MeWorkspace,
  type TransactionKind,
} from '@/shared/api'
import { MIN_DATE, addDays, localDate, maxDate } from '@/shared/i18n'
import { centsToApi, formatBRL, parseCents } from '@/shared/money'
import {
  enqueueTransaction,
  replaceOutboxItem,
  undoRecorded,
  useOnline,
  useOutbox,
  type OutboxItem,
  type UndoResult,
} from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { CategoryPicker } from './CategoryPicker'
import { ContextSheet, type ExpenseDate } from './ContextSheet'
import { Keypad } from './Keypad'
import { KindTabs } from './KindTabs'
import { kindTabId } from './kindTabId'

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

const KIND_PARAMS: Record<string, TransactionKind> = {
  gasto: 'EXPENSE',
  receita: 'INCOME',
  transferencia: 'TRANSFER',
}

const SAVED_MESSAGE = {
  EXPENSE: 'transaction.saved.expense',
  INCOME: 'transaction.saved.income',
  TRANSFER: 'transaction.saved.transfer',
} as const satisfies Record<TransactionKind, string>

const TITLE = {
  EXPENSE: 'transaction.title.expense',
  INCOME: 'transaction.title.income',
  TRANSFER: 'transaction.title.transfer',
} as const satisfies Record<TransactionKind, string>

const UNDO_MESSAGE = {
  undone: 'transaction.undone',
  offline: 'transaction.undoOffline',
  failed: 'transaction.undoFailed',
} as const satisfies Record<UndoResult, string>

/**
 * The new transaction, full screen (wireframe 3): Gasto, Receita or Transferência. `?tipo=receita` or
 * `?tipo=transferencia` opens on that tab and `?para=<account>` fills in where a transfer goes. With
 * `?corrigir=<id>`, it fixes a refused one.
 */
export function NewTransactionPage() {
  const userId = useMe().data?.user.id
  const workspace = useCurrentWorkspace()
  const [params] = useSearchParams()
  const fixId = params.get('corrigir')
  const outbox = useOutbox(userId, workspace?.id)
  const fixing = outbox?.find((item) => item.id === fixId && item.status === 'needs-attention')
  // A fix waits for the queue, so the form opens filled in rather than filling in later.
  const ready = userId !== undefined && workspace && (fixId === null || outbox !== undefined)
  const initialKind = KIND_PARAMS[params.get('tipo') ?? ''] ?? 'EXPENSE'

  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col gap-4 p-4">
      {ready ? (
        <TransactionForm
          key={fixing?.id ?? 'new'}
          userId={userId}
          workspace={workspace}
          fixing={fixing}
          initialKind={initialKind}
          initialToAccountId={params.get('para')}
        />
      ) : (
        <>
          <PageHeader kind={initialKind} />
          <p>
            <FormattedMessage id="common.loading" />
          </p>
        </>
      )}
    </main>
  )
}

/** The way back and the title, which names the kind being recorded (Ruling R17). */
function PageHeader({ kind }: { kind: TransactionKind }) {
  const intl = useIntl()
  const title = intl.formatMessage({ id: TITLE[kind] })
  useDocumentTitle(title)
  return (
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
  )
}

interface TransactionFormProps {
  userId: string
  workspace: MeWorkspace
  /** The refused item being fixed, which fills the form in. */
  fixing: OutboxItem | undefined
  /** The tab to open on, unless a fix says otherwise. */
  initialKind: TransactionKind
  /** Where a transfer goes, from `?para=`. */
  initialToAccountId: string | null
}

const accountOption =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary'

const outlineButton =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

function TransactionForm({
  userId,
  workspace,
  fixing,
  initialKind,
  initialToAccountId,
}: TransactionFormProps) {
  const intl = useIntl()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const online = useOnline()
  const panelId = useId()
  const expenseCategories = useCategories(workspace.id, 'EXPENSE')
  const incomeCategories = useCategories(workspace.id, 'INCOME')
  const accounts = useAccounts(workspace.id)
  const [kind, setKind] = useState<TransactionKind>(() => fixing?.payload.kind ?? initialKind)
  const [cents, setCents] = useState(() => (fixing ? parseCents(fixing.payload.amount.amount) : 0n))
  const [categoryId, setCategoryId] = useState(() => fixing?.payload.categoryId ?? undefined)
  const [date, setDate] = useState<ExpenseDate>(
    // The API sends dates as YYYY-MM-DD.
    () => (fixing?.payload.occurredOn as ExpenseDate | undefined) ?? 'today',
  )
  const [chosenAccountId, setChosenAccountId] = useState(
    () => fixing?.payload.accountId ?? readLastAccount(workspace.id),
  )
  const [toAccountId, setToAccountId] = useState(
    () => fixing?.payload.toAccountId ?? initialToAccountId,
  )
  const [description, setDescription] = useState(() => fixing?.payload.description ?? '')
  const [sheet, setSheet] = useState<'categories' | 'context' | null>(null)
  const [saving, setSaving] = useState(false)
  // State lags a render behind; the ref stops a double tap from queueing twice (Review Focus 1).
  const savingRef = useRef(false)

  const transfer = kind === 'TRANSFER'
  // An archived account takes no new transactions.
  const activeAccounts = (accounts.data ?? []).filter(({ archivedAt }) => archivedAt === null)
  const to = transfer ? activeAccounts.find(({ id }) => id === toAccountId) : undefined
  // De offers every account but starts elsewhere than Para; Para leaves De out.
  const notTo = activeAccounts.filter(({ id }) => id !== to?.id)
  const account = notTo.find(({ id }) => id === chosenAccountId) ?? notTo.at(0)
  const toChoices = activeAccounts.filter(({ id }) => id !== account?.id)
  // A transfer picks its accounts on the form, so it waits for them while they are on their way.
  const accountsLoading = accounts.data === undefined && accounts.fetchStatus === 'fetching'
  const fewAccounts = transfer && !accountsLoading && activeAccounts.length < 2

  const categories = kind === 'INCOME' ? incomeCategories : expenseCategories
  const categoryList = categories.data ?? []
  // Without a cache, only being offline means the categories cannot come; an online error retries.
  const noCategories = categories.data
    ? categories.data.length === 0
    : !online || categories.fetchStatus === 'paused'
  const categoryError = !categories.data && !noCategories && categories.isError
  // A fix can carry a category that is archived or not cached: it has no chip, so it cannot go.
  const categoryKnown = categoryList.some(({ id }) => id === categoryId)

  const now = new Date()
  const today = localDate(now, workspace.timezone)
  const lastDay = maxDate(now, workspace.timezone)
  const dateInRange =
    date === 'today' || date === 'yesterday' || (date >= MIN_DATE && date <= lastDay)
  const canSave =
    cents > 0n &&
    dateInRange &&
    account !== undefined &&
    (transfer ? to !== undefined : !noCategories && categoryKnown) &&
    !saving
  const dateLabel =
    date === 'today' || date === 'yesterday'
      ? intl.formatMessage({ id: date === 'today' ? 'common.today' : 'common.yesterday' })
      : intl.formatDate(`${date}T00:00:00Z`, { day: 'numeric', month: 'short', timeZone: 'UTC' })

  function changeKind(next: TransactionKind) {
    if (next === kind) return
    // The amount carries over; a category belongs to one kind, so it is chosen again.
    setKind(next)
    setCategoryId(undefined)
  }

  function chooseFrom(id: string) {
    setChosenAccountId(id)
    // Taking the Para account as De leaves Para to be chosen again.
    if (id === toAccountId) setToAccountId(null)
  }

  async function undo(id: string) {
    const result = await undoRecorded({
      id,
      workspaceId: workspace.id,
      online: navigator.onLine,
      remove: deleteTransaction,
    })
    if (result === 'undone') {
      void queryClient.invalidateQueries({ queryKey: queryKeys.workspace(workspace.id) })
    }
    toast.show({ message: intl.formatMessage({ id: UNDO_MESSAGE[result] }) })
  }

  function undoFailed(error: unknown) {
    console.error(error)
    toast.show({ message: intl.formatMessage({ id: 'common.actionFailed' }) })
  }

  async function save() {
    if (!canSave || !account || savingRef.current) return
    if (transfer ? !to : categoryId === undefined) return
    savingRef.current = true
    setSaving(true)
    // Hoje and Ontem become dates now, in the family's time zone (Review Focus 4).
    const savedToday = localDate(new Date(), workspace.timezone)
    const occurredOn =
      date === 'today' ? savedToday : date === 'yesterday' ? addDays(savedToday, -1) : date
    const payload: CreateTransactionRequest = {
      id: v7(),
      kind,
      amount: { amount: centsToApi(cents), currency: workspace.currency },
      occurredOn,
      accountId: account.id,
      categoryId: transfer ? null : categoryId,
      toAccountId: transfer ? (to?.id ?? null) : null,
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
    // The transaction is queued: the guard stays up, so nothing after this can queue it twice.
    writeLastAccount(workspace.id, account.id)
    toast.show({
      message: intl.formatMessage({ id: SAVED_MESSAGE[kind] }),
      // Undoing a fix would delete the refused transaction too (Ruling T11-correction-undo).
      action: fixing
        ? undefined
        : {
            label: intl.formatMessage({ id: 'transaction.undo' }),
            altText: intl.formatMessage({ id: 'transaction.undoAlt' }),
            onClick: () => {
              undo(payload.id).catch(undoFailed)
            },
          },
    })
    await navigate('/inicio')
  }

  return (
    <div className="flex flex-1 flex-col gap-4">
      <PageHeader kind={kind} />
      <KindTabs value={kind} onChange={changeKind} panelId={panelId} />
      <div
        role="tabpanel"
        id={panelId}
        aria-labelledby={kindTabId(panelId, kind)}
        className="flex flex-1 flex-col gap-4"
      >
        {transfer && accountsLoading ? (
          <p>
            <FormattedMessage id="common.loading" />
          </p>
        ) : fewAccounts ? (
          <div className="flex flex-col items-start gap-3 rounded-card bg-surface p-4">
            <p>
              <FormattedMessage id="transaction.transfer.needsTwoAccounts" />
            </p>
            <Link to="/mais/contas" className={`${outlineButton} flex items-center`}>
              <FormattedMessage id="transaction.transfer.addAccount" />
            </Link>
          </div>
        ) : (
          <>
            <output
              aria-label={intl.formatMessage({ id: 'expense.amount' })}
              aria-live="polite"
              className="block py-4 text-center text-4xl font-semibold tabular-nums"
            >
              {formatBRL(cents)}
            </output>
            {transfer ? (
              <div className="grid grid-cols-2 gap-4">
                <AccountChoice
                  legend={intl.formatMessage({ id: 'transaction.transfer.from' })}
                  name="from"
                  accounts={activeAccounts}
                  value={account?.id}
                  onChange={chooseFrom}
                />
                <AccountChoice
                  legend={intl.formatMessage({ id: 'transaction.transfer.to' })}
                  name="to"
                  accounts={toChoices}
                  value={to?.id}
                  onChange={setToAccountId}
                />
              </div>
            ) : noCategories ? (
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
                  className={outlineButton}
                >
                  <FormattedMessage id="common.retry" />
                </button>
              </div>
            ) : (
              <CategoryPicker
                key={kind}
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
              {transfer ? (
                <FormattedMessage id="transaction.transfer.context" values={{ date: dateLabel }} />
              ) : (
                <FormattedMessage
                  id="expense.context"
                  values={{ date: dateLabel, account: account?.name ?? '' }}
                />
              )}
              <span aria-hidden="true">✎</span>
            </button>
            <ContextSheet
              open={sheet === 'context'}
              onOpenChange={(open) => setSheet(open ? 'context' : null)}
              date={date}
              onDateChange={setDate}
              today={today}
              max={lastDay}
              account={
                transfer
                  ? undefined
                  : {
                      member: kind === 'EXPENSE',
                      accounts: activeAccounts,
                      accountId: account?.id,
                      onAccountChange: setChosenAccountId,
                    }
              }
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
          </>
        )}
      </div>
    </div>
  )
}

interface AccountChoiceProps {
  legend: string
  name: string
  accounts: AccountView[]
  value: string | undefined
  onChange: (accountId: string) => void
}

/** One end of a transfer, as radios. */
function AccountChoice({ legend, name, accounts, value, onChange }: AccountChoiceProps) {
  return (
    <fieldset>
      <legend className="mb-2 font-medium">{legend}</legend>
      {accounts.map(({ id, name: accountName }) => (
        <label key={id} className={accountOption}>
          <input type="radio" name={name} checked={id === value} onChange={() => onChange(id)} />
          {accountName}
        </label>
      ))}
    </fieldset>
  )
}
