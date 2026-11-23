import { useId, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import {
  useAccounts,
  useCategories,
  type AccountView,
  type CreateRecurringRuleRequest,
  type MeWorkspace,
  type PaymentMethod,
  type RecurringRuleView,
  type TransactionKind,
} from '@/shared/api'
import { localDate } from '@/shared/i18n'
import { centsToApi, parseCents, parseTypedAmount, typedAmount } from '@/shared/money'
import { CategoryPicker } from '@/features/transactions/CategoryPicker'
import { KindTabs } from '@/features/transactions/KindTabs'
import { kindTabId } from '@/features/transactions/kindTabId'
import { PaymentMethodChips } from '@/features/transactions/PaymentMethodChips'
import './register'

type Frequency = RecurringRuleView['frequency']

const FREQUENCIES: Frequency[] = ['WEEKLY', 'MONTHLY', 'YEARLY']
const INTERVALS = Array.from({ length: 12 }, (_, index) => index + 1)

interface RuleFormProps {
  workspace: MeWorkspace
  /** The rule being edited, as it was opened; the form starts from it. */
  rule?: RecurringRuleView
  /** Keeps Salvar off, for a call that is running or no connection to make it. */
  saveDisabled?: boolean
  /** Receives what the API's create and update calls take (an update adds the `version`). */
  onSave: (body: CreateRecurringRuleRequest) => void
}

const field = 'min-h-12 rounded-field border border-text-muted/30 bg-surface px-3 font-normal'
const label = 'flex flex-col gap-1 font-medium'

/**
 * A recurring rule's fields: what it records, as the entry form asks it (kind, amount, account,
 * category, method, description), then how it repeats. A rule carries no tags.
 */
export function RuleForm({ workspace, rule, saveDisabled = false, onSave }: RuleFormProps) {
  const intl = useIntl()
  const panelId = useId()
  const accounts = useAccounts(workspace.id).data
  const [kind, setKind] = useState<TransactionKind>(rule?.kind ?? 'EXPENSE')
  const expenseCategories = useCategories(workspace.id, 'EXPENSE').data
  const incomeCategories = useCategories(workspace.id, 'INCOME').data
  const [amount, setAmount] = useState(() =>
    rule ? typedAmount(parseCents(rule.amount.amount)) : '',
  )
  const [chosenAccountId, setChosenAccountId] = useState(rule?.accountId)
  const [toAccountId, setToAccountId] = useState(rule?.toAccountId ?? null)
  const [categoryId, setCategoryId] = useState(rule?.categoryId ?? undefined)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(
    rule?.paymentMethod ?? null,
  )
  const [description, setDescription] = useState(rule?.description ?? '')
  const [frequency, setFrequency] = useState<Frequency>(rule?.frequency ?? 'MONTHLY')
  const [interval, setInterval] = useState(rule?.interval ?? 1)
  const [startOn, setStartOn] = useState(
    () => rule?.startOn ?? localDate(new Date(), workspace.timezone),
  )
  const [endOn, setEndOn] = useState(rule?.endOn ?? '')

  const transfer = kind === 'TRANSFER'
  // An archived account takes nothing new; a rule keeps the ones it already has.
  const archivedLabel = intl.formatMessage({ id: 'transactions.filter.archived' })
  const usable = (accounts ?? [])
    .filter(
      ({ id, archivedAt }) =>
        archivedAt === null || id === rule?.accountId || id === rule?.toAccountId,
    )
    .map((each) =>
      each.archivedAt === null ? each : { ...each, name: `${each.name} ${archivedLabel}` },
    )
  const to = transfer ? usable.find(({ id }) => id === toAccountId) : undefined
  const notTo = usable.filter(({ id }) => id !== to?.id)
  const account = notTo.find(({ id }) => id === chosenAccountId) ?? notTo.at(0)
  const toChoices = usable.filter(({ id }) => id !== account?.id)

  const categories = (kind === 'INCOME' ? incomeCategories : expenseCategories) ?? []
  const keepArchivedId = rule?.kind === kind ? (rule.categoryId ?? undefined) : undefined
  const categoryKnown = categories.some(
    ({ id, archivedAt }) => id === categoryId && (archivedAt === null || id === keepArchivedId),
  )

  const cents = parseTypedAmount(amount)
  const valid =
    cents !== undefined &&
    account !== undefined &&
    startOn !== '' &&
    (endOn === '' || endOn >= startOn) &&
    (transfer ? to !== undefined : categoryKnown)

  function changeKind(next: TransactionKind) {
    if (next === kind) return
    setKind(next)
    // A category belongs to one kind, and a transfer is not paid for.
    setCategoryId(undefined)
    if (next === 'TRANSFER') setPaymentMethod(null)
  }

  function chooseFrom(id: string) {
    setChosenAccountId(id)
    if (id === toAccountId) setToAccountId(null)
  }

  function save() {
    if (!valid || cents === undefined || !account) return
    onSave({
      kind,
      amount: { amount: centsToApi(cents), currency: workspace.currency },
      accountId: account.id,
      categoryId: transfer ? null : categoryId,
      toAccountId: transfer ? (to?.id ?? null) : null,
      description: description.trim() || null,
      paymentMethod: transfer ? null : paymentMethod,
      frequency,
      interval,
      startOn,
      endOn: endOn || null,
    })
  }

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault()
        save()
      }}
    >
      <KindTabs value={kind} onChange={changeKind} panelId={panelId} />
      <div
        role="tabpanel"
        id={panelId}
        aria-labelledby={kindTabId(panelId, kind)}
        className="flex flex-col gap-4"
      >
        <label className={label}>
          <FormattedMessage id="expense.amount" />
          <input
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            className={`${field} tabular-nums`}
          />
        </label>
        {transfer ? (
          <div className="grid grid-cols-2 gap-4">
            <AccountChoice
              legend={intl.formatMessage({ id: 'transaction.transfer.from' })}
              name="from"
              accounts={usable}
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
        ) : (
          <>
            <AccountChoice
              legend={intl.formatMessage({ id: 'expense.account' })}
              name="account"
              accounts={usable}
              value={account?.id}
              onChange={chooseFrom}
            />
            <CategoryPicker
              key={kind}
              categories={categories}
              value={categoryId}
              keepArchivedId={keepArchivedId}
              onChange={setCategoryId}
              onSheetChange={() => undefined}
            />
            <PaymentMethodChips
              value={paymentMethod}
              onChange={setPaymentMethod}
              methods={workspace.paymentMethods}
            />
          </>
        )}
        <input
          type="text"
          aria-label={intl.formatMessage({ id: 'expense.description' })}
          placeholder={intl.formatMessage({ id: 'expense.description' })}
          maxLength={140}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          className={field}
        />
        <label className={label}>
          <FormattedMessage id="recurrences.repeat" />
          <select
            value={frequency}
            onChange={(event) => setFrequency(event.target.value as Frequency)}
            className={field}
          >
            {FREQUENCIES.map((each) => (
              <option key={each} value={each}>
                {intl.formatMessage({ id: `recurrences.frequency.${each}` as const })}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end gap-3">
          <label className={`${label} flex-1`}>
            <FormattedMessage id="recurrences.every" />
            <select
              value={interval}
              onChange={(event) => setInterval(Number(event.target.value))}
              className={field}
            >
              {INTERVALS.map((each) => (
                <option key={each} value={each}>
                  {each}
                </option>
              ))}
            </select>
          </label>
          <span className="flex min-h-12 items-center">
            {intl.formatMessage({ id: `recurrences.unit.${frequency}` as const }, { interval })}
          </span>
        </div>
        <label className={label}>
          <FormattedMessage id="recurrences.startOn" />
          <input
            type="date"
            value={startOn}
            onChange={(event) => setStartOn(event.target.value)}
            className={field}
          />
        </label>
        <label className={label}>
          <FormattedMessage id="recurrences.endOn" />
          <input
            type="date"
            value={endOn}
            min={startOn}
            onChange={(event) => setEndOn(event.target.value)}
            className={field}
          />
        </label>
      </div>
      <button
        type="submit"
        disabled={!valid || saveDisabled}
        className="min-h-14 rounded-field bg-primary text-lg font-semibold text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        <FormattedMessage id="expense.save" />
      </button>
    </form>
  )
}

interface AccountChoiceProps {
  legend: string
  name: string
  accounts: AccountView[]
  value: string | undefined
  onChange: (accountId: string) => void
}

/** Accounts as radios: the one an expense or income is on, or one end of a transfer. */
function AccountChoice({ legend, name, accounts, value, onChange }: AccountChoiceProps) {
  return (
    <fieldset>
      <legend className="mb-1 font-medium">{legend}</legend>
      {accounts.map(({ id, name: accountName }) => (
        <label
          key={id}
          className="flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary"
        >
          <input type="radio" name={name} checked={id === value} onChange={() => onChange(id)} />
          {accountName}
        </label>
      ))}
    </fieldset>
  )
}
