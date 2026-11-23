import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { AccountView, MeWorkspace, TransactionView } from '@/shared/api'
import { centsToApi, parseCents, parseTypedAmount, typedAmount } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { Sheet } from '@/shared/ui/Sheet'
import './more.register'
import { canActOnPlanned } from './listItems'
import { usePlannedActions } from './usePlannedActions'

interface PlannedSheetProps {
  workspace: MeWorkspace
  planned: TransactionView
  accounts: Pick<AccountView, 'id' | 'name' | 'archivedAt'>[]
  onClose: () => void
}

const field = 'min-h-12 rounded-field border border-text-muted/30 bg-surface px-3'
const button =
  'min-h-12 rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/**
 * A planned transaction opened: its amount, date and account can be adjusted before Confirmar, and
 * Pular drops it (Desfazer brings it back). Both need the connection.
 */
export function PlannedSheet({ workspace, planned, accounts, onClose }: PlannedSheetProps) {
  const intl = useIntl()
  const online = useOnline()
  const { confirmPlanned, skipPlanned, busy } = usePlannedActions(workspace, onClose)
  const [amount, setAmount] = useState(() => typedAmount(parseCents(planned.amount.amount)))
  const [date, setDate] = useState(planned.scheduledOn ?? planned.occurredOn)
  const [accountId, setAccountId] = useState(planned.accountId)
  const cents = parseTypedAmount(amount)
  const choices = accounts.filter(({ id, archivedAt }) => archivedAt === null || id === accountId)
  const canAct = canActOnPlanned(workspace.role, planned, new Set(accounts.map(({ id }) => id)))
  const disabled = !online || busy

  async function confirm() {
    if (cents === undefined || date === '') return
    const done = await confirmPlanned(planned, {
      amount: { amount: centsToApi(cents), currency: workspace.currency },
      occurredOn: date,
      accountId,
    })
    // One that changed stays open for a second look; the notice says why.
    if (done) onClose()
  }

  async function skip() {
    if (await skipPlanned(planned)) onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'planned.sheetTitle' })}
    >
      <div className="flex flex-col gap-4">
        <label className="flex flex-col gap-1 font-medium">
          <FormattedMessage id="expense.amount" />
          <input
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            aria-invalid={cents === undefined}
            className={`${field} font-normal tabular-nums`}
          />
        </label>
        <label className="flex flex-col gap-1 font-medium">
          <FormattedMessage id="planned.date" />
          <input
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
            className={`${field} font-normal`}
          />
        </label>
        <fieldset>
          <legend className="mb-1 font-medium">
            <FormattedMessage id="expense.account" />
          </legend>
          {choices.map(({ id, name }) => (
            <label key={id} className="flex min-h-12 cursor-pointer items-center gap-3 px-1">
              <input
                type="radio"
                name="planned-account"
                checked={id === accountId}
                onChange={() => setAccountId(id)}
              />
              {name}
            </label>
          ))}
        </fieldset>
        {canAct && (
          <>
            {!online && (
              <p className="text-sm text-text-muted">
                <FormattedMessage id="transactions.offlineEditing" />
              </p>
            )}
            <div className="flex flex-col gap-2">
              <button
                type="button"
                disabled={disabled || cents === undefined || date === ''}
                onClick={() => void confirm()}
                className={`${button} bg-primary text-on-primary`}
              >
                <FormattedMessage id="planned.confirm" />
              </button>
              <button
                type="button"
                disabled={disabled}
                onClick={() => void skip()}
                className={`${button} border border-primary text-primary`}
              >
                <FormattedMessage id="planned.skip" />
              </button>
            </div>
          </>
        )}
      </div>
    </Sheet>
  )
}
