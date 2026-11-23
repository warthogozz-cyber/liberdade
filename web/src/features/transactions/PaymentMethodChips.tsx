import { useIntl } from 'react-intl'
import type { PaymentMethod } from '@/shared/api'

interface PaymentMethodChipsProps {
  value: PaymentMethod | null
  onChange: (method: PaymentMethod | null) => void
  /** The family's methods, in the order they are shown. */
  methods: PaymentMethod[]
}

/** How the transaction was paid, as toggle buttons; tapping the marked one clears the choice. */
export function PaymentMethodChips({ value, onChange, methods }: PaymentMethodChipsProps) {
  const intl = useIntl()
  return (
    <div
      role="group"
      aria-label={intl.formatMessage({ id: 'payment.label' })}
      className="flex flex-wrap gap-2"
    >
      {methods.map((method) => {
        const pressed = method === value
        return (
          <button
            key={method}
            type="button"
            aria-pressed={pressed}
            onClick={() => onChange(pressed ? null : method)}
            className={`min-h-12 rounded-field border px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
              pressed
                ? 'border-primary bg-primary text-on-primary'
                : 'border-text-muted/30 bg-surface'
            }`}
          >
            {intl.formatMessage({ id: `payment.${method}` })}
          </button>
        )
      })}
    </div>
  )
}
