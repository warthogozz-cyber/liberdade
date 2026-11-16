import { FormattedMessage, useIntl } from 'react-intl'
import type { AccountView } from '@/shared/api'
import { Sheet } from '@/shared/ui'

/** Hoje and Ontem stay words until saving, which works out the date then (Review Focus 4). */
export type ExpenseDate = 'today' | 'yesterday' | `${number}-${number}-${number}`

interface ContextSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  date: ExpenseDate
  onDateChange: (date: ExpenseDate) => void
  /** Today in the family's time zone, where "Outra data" starts. */
  today: string
  accounts: AccountView[]
  accountId: string | undefined
  onAccountChange: (accountId: string) => void
}

const option =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary'

/** The expense's date and account. Who spent it is always the user for now (D1). */
export function ContextSheet({
  open,
  onOpenChange,
  date,
  onDateChange,
  today,
  accounts,
  accountId,
  onAccountChange,
}: ContextSheetProps) {
  const intl = useIntl()
  const other = date !== 'today' && date !== 'yesterday'
  const otherDate = intl.formatMessage({ id: 'expense.otherDate' })

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={intl.formatMessage({ id: 'expense.contextSheetTitle' })}
    >
      <div className="flex flex-col gap-6">
        <fieldset>
          <legend className="mb-2 font-medium">
            <FormattedMessage id="expense.date" />
          </legend>
          <label className={option}>
            <input
              type="radio"
              name="date"
              checked={date === 'today'}
              onChange={() => onDateChange('today')}
            />
            <FormattedMessage id="common.today" />
          </label>
          <label className={option}>
            <input
              type="radio"
              name="date"
              checked={date === 'yesterday'}
              onChange={() => onDateChange('yesterday')}
            />
            <FormattedMessage id="common.yesterday" />
          </label>
          <label className={option}>
            <input
              type="radio"
              name="date"
              checked={other}
              onChange={() => onDateChange(today as ExpenseDate)}
            />
            {otherDate}
          </label>
          {other && (
            <input
              type="date"
              aria-label={otherDate}
              value={date}
              required
              onChange={(event) => {
                // A date input's value is empty or YYYY-MM-DD.
                if (event.target.value) onDateChange(event.target.value as ExpenseDate)
              }}
              className="mt-2 min-h-12 w-full rounded-field border border-text-muted/30 px-3"
            />
          )}
        </fieldset>
        <fieldset>
          <legend className="mb-2 font-medium">
            <FormattedMessage id="expense.account" />
          </legend>
          {accounts.map((account) => (
            <label key={account.id} className={option}>
              <input
                type="radio"
                name="account"
                checked={account.id === accountId}
                onChange={() => onAccountChange(account.id)}
              />
              {account.name}
            </label>
          ))}
        </fieldset>
        <p className="text-text-muted">
          <FormattedMessage id="expense.member" />
        </p>
      </div>
    </Sheet>
  )
}
