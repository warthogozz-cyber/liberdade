import { useDeferredValue, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from '../messages.pt-BR'
import { useFamilyProducts } from '../useReceipts'
import { MAX_PRODUCT_NAME, PRODUCT_UNITS, type ProductUnit } from './productUnits'

registerMessages(marketsMessages)

/** A product the family already has, or a name that becomes a new one when the receipt is saved. */
export type ProductChoice =
  | { kind: 'existing'; id: string; name: string; unit: ProductUnit | null }
  | { kind: 'new'; name: string }

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'
const option = `flex min-h-12 w-full items-center rounded-field border border-border px-3 text-left ${focus}`

const asUnit = (unit: string): ProductUnit | null =>
  PRODUCT_UNITS.find((each) => each === unit) ?? null

const fold = (text: string) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

interface ProductPickerProps {
  ws: string
  value: ProductChoice | null
  onChange: (choice: ProductChoice | null) => void
}

/** Searches the family's products as the person types; a name with no match becomes "Novo: …". */
export function ProductPicker({ ws, value, onChange }: ProductPickerProps) {
  const intl = useIntl()
  const [text, setText] = useState('')
  const query = useDeferredValue(text.trim())
  // Nothing is asked of the API until something is typed.
  const products = useFamilyProducts(query === '' ? undefined : ws, query)
  const label = intl.formatMessage({ id: 'markets.receipt.product.label' })

  if (value !== null) {
    return (
      <div className="flex items-center justify-between gap-2">
        <span className="min-w-0 truncate font-medium">{value.name}</span>
        <button
          type="button"
          onClick={() => onChange(null)}
          className={`min-h-12 px-3 font-medium text-primary underline ${focus}`}
        >
          <FormattedMessage id="markets.receipt.product.change" />
        </button>
      </div>
    )
  }

  const typed = text.trim()
  const found = query === typed ? (products.data ?? []) : []
  const exact = found.some((each) => fold(each.name) === fold(typed))
  return (
    <div className="flex flex-col gap-2">
      <label className="flex flex-col gap-1 text-sm font-medium">
        {label}
        <input
          type="text"
          value={text}
          maxLength={MAX_PRODUCT_NAME}
          autoComplete="off"
          onChange={(event) => setText(event.target.value)}
          className={`${field} font-normal`}
        />
      </label>
      {typed !== '' && (
        <ul className="flex flex-col gap-2">
          {found.map((each) => (
            <li key={each.id}>
              <button
                type="button"
                onClick={() =>
                  onChange({
                    kind: 'existing',
                    id: each.id,
                    name: each.name,
                    unit: asUnit(each.unit),
                  })
                }
                className={option}
              >
                {each.name}
              </button>
            </li>
          ))}
          {!exact && (
            <li>
              <button
                type="button"
                onClick={() => onChange({ kind: 'new', name: typed })}
                className={option}
              >
                <FormattedMessage id="markets.receipt.product.new" values={{ name: typed }} />
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
