import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { SaveReceiptItem } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { centsToApi, parseTypedAmount } from '@/shared/money'
import { marketsMessages } from '../messages.pt-BR'
import { ProductPicker, type ProductChoice } from './ProductPicker'
import { PRODUCT_UNITS, type ProductUnit } from './productUnits'

registerMessages(marketsMessages)

/** The most items one receipt may carry (the API refuses more). */
const MAX_ITEMS = 200

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-60 ${focus}`
const primary = `flex min-h-12 items-center justify-center rounded-field border border-primary bg-primary px-4 font-medium text-on-primary disabled:opacity-60 ${focus}`

interface Row {
  key: number
  product: ProductChoice | null
  unit: ProductUnit
  quantity: string
  price: string
}

const blankRow = (key: number): Row => ({ key, product: null, unit: 'UN', quantity: '', price: '' })

/** A typed quantity ("1,5") as the API's decimal text; empty is "no quantity"; null is invalid. */
function quantityText(typed: string): string | null | undefined {
  const clean = typed.trim()
  if (clean === '') return undefined
  const match = /^(\d{1,15})(?:[.,](\d{1,4}))?$/.exec(clean)
  if (!match) return null
  const text = match[2] === undefined ? match[1]! : `${match[1]}.${match[2]}`
  return Number(text) > 0 ? text : null
}

/** One row as the API takes it, or null while the row is not complete. */
function toItem(row: Row): SaveReceiptItem | null {
  if (row.product === null) return null
  const cents = parseTypedAmount(row.price)
  const quantity = quantityText(row.quantity)
  if (cents === undefined || quantity === null) return null
  return {
    productId: row.product.kind === 'existing' ? row.product.id : null,
    newProductName: row.product.kind === 'new' ? row.product.name : null,
    unit: row.unit,
    quantity: quantity ?? null,
    unitPrice: centsToApi(cents),
  }
}

interface ReceiptItemsStepProps {
  ws: string
  /** The key's state, for the notice that the automatic reading does not exist yet. */
  uf: string
  /** The QR's own link; only present when the person gave one without the buyer's document. */
  sefazUrl?: string
  /** The date to start from; without one the person must fill it in. */
  defaultDate: string | undefined
  pending: boolean
  error: string | null
  onSubmit: (value: { issuedOn: string; items: SaveReceiptItem[] }) => void
}

/** The third step: the purchase date and one row per item with its unit price. */
export function ReceiptItemsStep({
  ws,
  uf,
  sefazUrl,
  defaultDate,
  pending,
  error,
  onSubmit,
}: ReceiptItemsStepProps) {
  const intl = useIntl()
  const [issuedOn, setIssuedOn] = useState(defaultDate ?? '')
  const [rows, setRows] = useState<Row[]>([blankRow(0)])
  const [nextKey, setNextKey] = useState(1)

  const items = rows.map(toItem)
  const complete = issuedOn !== '' && items.every((item) => item !== null)

  const change = (key: number, patch: Partial<Row>) =>
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  const choose = (key: number, product: ProductChoice | null) =>
    change(key, {
      product,
      ...(product?.kind === 'existing' && product.unit !== null && { unit: product.unit }),
    })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!complete || pending) return
    onSubmit({ issuedOn, items: items as SaveReceiptItem[] })
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <p>
        <FormattedMessage id="markets.receipt.items.noReader" values={{ uf }} />
      </p>
      {sefazUrl !== undefined && (
        <a
          href={sefazUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={`self-start font-medium text-primary underline ${focus}`}
        >
          <FormattedMessage id="markets.receipt.items.sefaz" />
        </a>
      )}

      <label className="flex flex-col gap-1 text-sm font-medium">
        <FormattedMessage id="markets.receipt.items.date" />
        <input
          type="date"
          value={issuedOn}
          required
          onChange={(event) => setIssuedOn(event.target.value)}
          className={`${field} font-normal`}
        />
      </label>

      <ol className="flex flex-col gap-4">
        {rows.map((row, index) => (
          <li key={row.key} className="flex flex-col gap-3 rounded-card bg-surface p-4">
            <p className="text-sm text-text-muted">
              <FormattedMessage id="markets.receipt.items.item" values={{ n: index + 1 }} />
            </p>
            <ProductPicker
              ws={ws}
              value={row.product}
              onChange={(product) => choose(row.key, product)}
            />
            <div className="grid grid-cols-2 gap-3">
              <label className="flex flex-col gap-1 text-sm font-medium">
                <FormattedMessage id="markets.receipt.items.unit" />
                <select
                  value={row.unit}
                  onChange={(event) => change(row.key, { unit: event.target.value as ProductUnit })}
                  className={`${field} font-normal`}
                >
                  {PRODUCT_UNITS.map((unit) => (
                    <option key={unit} value={unit}>
                      {intl.formatMessage({ id: `markets.receipt.unit.${unit}` })}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                <FormattedMessage id="markets.receipt.items.quantity" />
                <input
                  type="text"
                  inputMode="decimal"
                  value={row.quantity}
                  onChange={(event) => change(row.key, { quantity: event.target.value })}
                  className={`${field} font-normal`}
                />
              </label>
            </div>
            <label className="flex flex-col gap-1 text-sm font-medium">
              <FormattedMessage id="markets.receipt.items.price" />
              <input
                type="text"
                inputMode="decimal"
                value={row.price}
                onChange={(event) => change(row.key, { price: event.target.value })}
                className={`${field} font-normal`}
              />
            </label>
            {rows.length > 1 && (
              <button
                type="button"
                aria-label={intl.formatMessage(
                  { id: 'markets.receipt.items.remove' },
                  { n: index + 1 },
                )}
                onClick={() => setRows((current) => current.filter((each) => each.key !== row.key))}
                className={`${outline} self-start`}
              >
                <FormattedMessage id="markets.receipt.items.removeShort" />
              </button>
            )}
          </li>
        ))}
      </ol>

      <button
        type="button"
        disabled={rows.length >= MAX_ITEMS}
        onClick={() => {
          setRows((current) => [...current, blankRow(nextKey)])
          setNextKey(nextKey + 1)
        }}
        className={`${outline} self-start`}
      >
        <FormattedMessage id="markets.receipt.items.add" />
      </button>
      {rows.length >= MAX_ITEMS && (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.receipt.items.max" />
        </p>
      )}

      {error !== null && <p role="alert">{error}</p>}
      <button type="submit" disabled={!complete || pending} className={primary}>
        <FormattedMessage id="markets.receipt.items.save" />
      </button>
    </form>
  )
}
