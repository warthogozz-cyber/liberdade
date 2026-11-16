import { useId, useRef, useState, type ReactNode } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate } from 'react-router'
import { v7 } from 'uuid'
import { useQueryClient } from '@tanstack/react-query'
import {
  deleteTransaction,
  queryKeys,
  useAccounts,
  useCategories,
  type AccountView,
  type CreateTransactionRequest,
  type MeWorkspace,
  type TransactionKind,
  type TransactionView,
  type UpdateTransactionRequest,
} from '@/shared/api'
import { MIN_DATE, addDays, localDate, maxDate } from '@/shared/i18n'
import { centsToApi, formatBRL, parseCents } from '@/shared/money'
import {
  enqueueTransaction,
  replaceOutboxItem,
  undoRecorded,
  useOnline,
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

/** The way back and the title, which names the kind being recorded (Ruling R17). */
export function PageHeader({ kind, editMonth }: { kind: TransactionKind; editMonth?: string }) {
  const intl = useIntl()
  const editing = editMonth !== undefined
  const title = intl.formatMessage({ id: editing ? 'transaction.title.edit' : TITLE[kind] })
  useDocumentTitle(title)
  return (
    <header className="flex items-center gap-2">
      <Link
        to={editing ? `/lancamentos${editMonth ? `?mes=${editMonth}` : ''}` : '/inicio'}
        aria-label={intl.formatMessage({ id: 'expense.back' })}
        className="flex size-12 items-center justify-center rounded-field text-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <span aria-hidden="true">←</span>
      </Link>
      <h1 className="text-xl font-semibold">{title}</h1>
    </header>
  )
}

export interface TransactionFormProps {
  userId: string
  workspace: MeWorkspace
  /** The refused item being fixed, which fills the form in. */
  fixing: OutboxItem | undefined
  /** The tab to open on, unless a fix says otherwise. */
  initialKind: TransactionKind
  /** Where a transfer goes, from `?para=`. */
  initialToAccountId: string | null
  /** The saved transaction being edited, as it was opened; the form saves with a PATCH. */
  editing?: TransactionView
  /** The month of the list an edit goes back to. */
  month?: string
  /** Saves an edit; true once it is saved. Given only by the lazy editing module (ADR-0007). */
  onEdit?: (body: UpdateTransactionRequest) => Promise<boolean>
  /** The editing module is saving on its own (a re-send after a conflict): no second save meanwhile. */
  busy?: boolean
  /** Rendered after the form: the editing module's conflict dialogs. */
  children?: ReactNode
}

const accountOption =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary'

const outlineButton =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

export function TransactionForm({
  userId,
  workspace,
  fixing,
  initialKind,
  initialToAccountId,
  editing,
  month,
  onEdit,
  busy = false,
  children,
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
  const [kind, setKind] = useState<TransactionKind>(
    () => editing?.kind ?? fixing?.payload.kind ?? initialKind,
  )
  const [cents, setCents] = useState(() => {
    const opened = editing?.amount.amount ?? fixing?.payload.amount.amount
    return opened ? parseCents(opened) : 0n
  })
  const [categoryId, setCategoryId] = useState(
    () => editing?.categoryId ?? fixing?.payload.categoryId ?? undefined,
  )
  const [date, setDate] = useState<ExpenseDate>(
    // The API sends dates as YYYY-MM-DD.
    () =>
      ((editing?.occurredOn ?? fixing?.payload.occurredOn) as ExpenseDate | undefined) ?? 'today',
  )
  const [chosenAccountId, setChosenAccountId] = useState(
    () => editing?.accountId ?? fixing?.payload.accountId ?? readLastAccount(workspace.id),
  )
  const [toAccountId, setToAccountId] = useState(
    () => editing?.toAccountId ?? fixing?.payload.toAccountId ?? initialToAccountId,
  )
  // Only a person's own pick is sent: a member left alone stays the signed-in one (R2).
  const [chosenMemberId, setChosenMemberId] = useState<string | undefined>(
    () => fixing?.payload.memberId ?? undefined,
  )
  // What the context line calls the chosen member; the picker reports it, so no naming loads here.
  const [chosenLabel, setChosenLabel] = useState<string>()
  const [description, setDescription] = useState(
    () => editing?.description ?? fixing?.payload.description ?? '',
  )
  const [sheet, setSheet] = useState<'categories' | 'context' | null>(null)
  const [saving, setSaving] = useState(false)
  // State lags a render behind; the ref stops a double tap from queueing twice (Review Focus 1).
  const savingRef = useRef(false)

  const transfer = kind === 'TRANSFER'
  // An archived account takes no new transactions; an edit keeps the ones the transaction has (R9).
  const archivedLabel = intl.formatMessage({ id: 'transactions.filter.archived' })
  const activeAccounts = (accounts.data ?? [])
    .filter(
      ({ id, archivedAt }) =>
        archivedAt === null || id === editing?.accountId || id === editing?.toAccountId,
    )
    .map((each) =>
      each.archivedAt === null ? each : { ...each, name: `${each.name} ${archivedLabel}` },
    )
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
  // Only an edit keeps its own archived category on offer (see keepArchivedId).
  const keepArchivedId = editing?.kind === kind ? (editing.categoryId ?? undefined) : undefined
  const categoryKnown = categoryList.some(
    ({ id, archivedAt }) => id === categoryId && (archivedAt === null || id === keepArchivedId),
  )

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
    !saving &&
    !busy
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
    if (!canSave || !account || savingRef.current || busy) return
    if (transfer ? !to : categoryId === undefined) return
    savingRef.current = true
    setSaving(true)
    // Hoje and Ontem become dates now, in the family's time zone (Review Focus 4).
    const savedToday = localDate(new Date(), workspace.timezone)
    const occurredOn =
      date === 'today' ? savedToday : date === 'yesterday' ? addDays(savedToday, -1) : date
    if (editing && onEdit) {
      const saved = await onEdit({
        kind,
        amount: { amount: centsToApi(cents), currency: workspace.currency },
        occurredOn,
        accountId: account.id,
        categoryId: transfer ? null : categoryId,
        toAccountId: transfer ? (to?.id ?? null) : null,
        description: description.trim() || null,
        ...(chosenMemberId && kind === 'EXPENSE' ? { memberId: chosenMemberId } : {}),
        version: editing.version,
      })
      if (!saved) {
        savingRef.current = false
        setSaving(false)
      }
      return
    }
    const payload: CreateTransactionRequest = {
      id: v7(),
      kind,
      amount: { amount: centsToApi(cents), currency: workspace.currency },
      occurredOn,
      accountId: account.id,
      categoryId: transfer ? null : categoryId,
      toAccountId: transfer ? (to?.id ?? null) : null,
      description: description.trim() || null,
      ...(chosenMemberId && chosenMemberId !== workspace.membershipId && kind === 'EXPENSE'
        ? { memberId: chosenMemberId }
        : {}),
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
      <PageHeader kind={kind} editMonth={editing ? (month ?? '') : undefined} />
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
                keepArchivedId={keepArchivedId}
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
                  values={{
                    date: dateLabel,
                    account: account?.name ?? '',
                    who:
                      chosenMemberId && chosenMemberId !== workspace.membershipId && chosenLabel
                        ? chosenLabel
                        : intl.formatMessage({ id: 'transactions.detail.byMe' }),
                  }}
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
                      // An expense has someone who spent it; a Youth only ever spends their own.
                      member:
                        kind === 'EXPENSE' && workspace.role !== 'YOUTH'
                          ? {
                              workspaceId: workspace.id,
                              selfId: workspace.membershipId,
                              value: chosenMemberId ?? editing?.memberId ?? workspace.membershipId,
                              onChange: (memberId, label) => {
                                setChosenMemberId(memberId)
                                setChosenLabel(label)
                              },
                            }
                          : undefined,
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
      {children}
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
