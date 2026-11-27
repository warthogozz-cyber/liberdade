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

/** How many list items a blank row offers: a long list would bury the field. Typing still finds the rest. */
const BLANK_SUGGESTIONS = 10

const fold = (text: string) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

/** An item of the shopping list, as the picker offers it before the other products. */
export interface ListItem {
  id: string
  name: string
  unit: string
}

interface ProductPickerProps {
  ws: string
  value: ProductChoice | null
  onChange: (choice: ProductChoice | null) => void
  /** The search text, when the caller keeps it (so it outlives this picker); kept here otherwise. */
  text?: string
  onTextChange?: (text: string) => void
  /** The id of the search field, so a form can send focus to it. */
  inputId?: string
  invalid?: boolean
  describedBy?: string
  /** Whether a name with no match may become a new product (on by default). */
  allowNew?: boolean
  /** The shopping list's items: offered first, and before anything is typed (none by default). */
  listItems?: readonly ListItem[]
}

/** Searches the family's products as the person types; a name with no match becomes "Novo: …". */
export function ProductPicker({
  ws,
  value,
  onChange,
  text: keptText,
  onTextChange,
  inputId,
  invalid,
  describedBy,
  allowNew = true,
  listItems = [],
}: ProductPickerProps) {
  const intl = useIntl()
  const [ownText, setOwnText] = useState('')
  const text = keptText ?? ownText
  const setText = onTextChange ?? setOwnText
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
  // A failed search also counts as settled, so the person is never stuck without "Novo".
  // "Novo" only once the search for exactly what is typed has answered: aliases are per market, so an
  // early "Novo" could create a second product for a name the family already has.
  const settled = query === typed && !products.isFetching && !products.isPending
  const fromList =
    typed === ''
      ? listItems.slice(0, BLANK_SUGGESTIONS)
      : listItems.filter((each) => fold(each.name).includes(fold(typed)))
  const found = settled
    ? (products.data ?? []).filter((each) => !fromList.some((listed) => listed.id === each.id))
    : []
  const candidates = [...fromList, ...found]
  const exact = candidates.some((each) => fold(each.name) === fold(typed))
  const showing = typed === '' ? fromList.length > 0 : settled
  return (
    <div className="flex flex-col gap-2">
      <label className="flex flex-col gap-1 text-sm font-medium">
        {label}
        <input
          id={inputId}
          type="text"
          value={text}
          aria-invalid={invalid ? true : undefined}
          aria-describedby={describedBy}
          maxLength={MAX_PRODUCT_NAME}
          autoComplete="off"
          onChange={(event) => setText(event.target.value)}
          className={`${field} font-normal`}
        />
      </label>
      {typed !== '' && !settled && (
        <p role="status" className="text-sm text-text-muted">
          <FormattedMessage id="markets.receipt.product.searching" />
        </p>
      )}
      {showing && (
        <ul className="flex flex-col gap-2">
          {candidates.map((each) => (
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
          {typed !== '' && settled && !exact && allowNew && (
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
