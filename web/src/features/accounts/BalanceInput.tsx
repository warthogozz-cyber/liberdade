import { useIntl } from 'react-intl'
import { Keypad } from '@/features/transactions/Keypad'
import { formatBRL } from '@/shared/money'
import { signedCents } from './signedCents'

interface BalanceInputProps {
  /** Names the amount, for a screen reader. */
  label: string
  cents: bigint
  negative: boolean
  onCents: (update: (cents: bigint) => bigint) => void
  onNegative: (negative: boolean) => void
}

/** A balance typed on the keypad. The keypad types positive amounts, so the sign is its own switch. */
export function BalanceInput({ label, cents, negative, onCents, onNegative }: BalanceInputProps) {
  const intl = useIntl()
  return (
    <div className="flex flex-col gap-3">
      <output
        aria-label={label}
        aria-live="polite"
        className="block py-2 text-center text-3xl font-semibold tabular-nums"
      >
        {formatBRL(signedCents(cents, negative))}
      </output>
      <label className="flex min-h-12 items-center gap-3">
        <input
          type="checkbox"
          role="switch"
          checked={negative}
          onChange={(event) => onNegative(event.target.checked)}
          className="size-6"
        />
        {intl.formatMessage({ id: 'accounts.field.negative' })}
      </label>
      <Keypad onChange={onCents} keyboard />
    </div>
  )
}
