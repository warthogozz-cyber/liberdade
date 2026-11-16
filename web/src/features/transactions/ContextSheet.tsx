import { lazy, Suspense } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { AccountView } from '@/shared/api'
import { MIN_DATE } from '@/shared/i18n'
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
  /** The last day "Outra data" offers, a year ahead in the family's time zone. */
  max: string
  /** The account to choose; left out for a transfer, which picks its two accounts on the form. */
  account?: {
    /**
     * "Quem", given only for an expense a non-Youth records: nobody else spends a Youth's money.
     * It loads on its own, so the family's catalogue stays out of the recording screen.
     */
    member?: {
      workspaceId: string
      selfId: string
      value: string
      onChange: (memberId: string) => void
    }
    accounts: AccountView[]
    accountId: string | undefined
    onAccountChange: (accountId: string) => void
  }
}

const MemberPicker = lazy(() => import('./MemberPicker'))

const option =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary'

/**
 * The transaction's date and, unless it is a transfer, its account and who spent it.
 */
export function ContextSheet({
  open,
  onOpenChange,
  date,
  onDateChange,
  today,
  max,
  account,
}: ContextSheetProps) {
  const intl = useIntl()
  const other = date !== 'today' && date !== 'yesterday'
  const otherDate = intl.formatMessage({ id: 'expense.otherDate' })

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={intl.formatMessage({ id: account ? 'expense.contextSheetTitle' : 'expense.date' })}
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
              min={MIN_DATE}
              max={max}
              required
              onChange={(event) => {
                // A date input's value is empty or YYYY-MM-DD.
                if (event.target.value) onDateChange(event.target.value as ExpenseDate)
              }}
              className="mt-2 min-h-12 w-full rounded-field border border-text-muted/30 px-3"
            />
          )}
        </fieldset>
        {account && (
          <>
            <fieldset>
              <legend className="mb-2 font-medium">
                <FormattedMessage id="expense.account" />
              </legend>
              {account.accounts.map(({ id, name }) => (
                <label key={id} className={option}>
                  <input
                    type="radio"
                    name="account"
                    checked={id === account.accountId}
                    onChange={() => account.onAccountChange(id)}
                  />
                  {name}
                </label>
              ))}
            </fieldset>
            {account.member && (
              <Suspense fallback={null}>
                <MemberPicker {...account.member} />
              </Suspense>
            )}
          </>
        )}
      </div>
    </Sheet>
  )
}
