import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { SaveReceiptItem } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { useShoppingList } from '../list/useShoppingList'
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

export interface Row {
  key: number
  product: ProductChoice | null
  /** What is typed in the product search, kept while no product is chosen. */
  search: string
  unit: ProductUnit
  quantity: string
  price: string
}

/** What the person has typed so far; the page keeps it so going back a step loses nothing. */
export interface ItemsDraft {
  issuedOn: string
  rows: Row[]
  nextKey: number
}

const blankRow = (key: number): Row => ({
  key,
  product: null,
  search: '',
  unit: 'UN',
  quantity: '',
  price: '',
})

/** A typed quantity ("1,5") as the API's decimal text; empty is "no quantity"; null is invalid. */
function quantityText(typed: string): string | null | undefined {
  const clean = typed.trim()
  if (clean === '') return undefined
  const match = /^(\d{1,15})(?:[.,](\d{1,4}))?$/.exec(clean)
  if (!match) return null
  const text = match[2] === undefined ? match[1]! : `${match[1]}.${match[2]}`
  return Number(text) > 0 ? text : null
}

/**
 * A typed unit price as the API's decimal text, or null when it is not above zero or has more than four
 * decimals. Receipts print unit prices with three decimals, so this is wider than money amounts. A dot
 * is a decimal mark, except in a thousands-grouped number that has a comma ("1.299,90").
 */
function unitPriceText(typed: string): string | null {
  const clean = typed.trim().replace(/\s/g, '')
  const grouped = /^(\d{1,3}(?:\.\d{3})+),(\d{1,4})$/.exec(clean)
  const plain = /^(\d{1,15})(?:[.,](\d{1,4}))?$/.exec(clean)
  const match = grouped ?? plain
  if (!match) return null
  const whole = match[1]!.replace(/\./g, '')
  const fraction = (match[2] ?? '').padEnd(2, '0')
  if (whole.length > 15 || !/[1-9]/.test(whole + fraction)) return null
  return `${whole}.${fraction}`
}

interface RowProblems {
  product: boolean
  quantity: boolean
  price: boolean
}

const problemsOf = (row: Row): RowProblems => ({
  product: row.product === null,
  quantity: quantityText(row.quantity) === null,
  price: unitPriceText(row.price) === null,
})

/** One row as the API takes it; only call it for a row without problems. */
function toItem(row: Row): SaveReceiptItem {
  const product = row.product as ProductChoice
  return {
    productId: product.kind === 'existing' ? product.id : null,
    newProductName: product.kind === 'new' ? product.name : null,
    unit: row.unit,
    quantity: quantityText(row.quantity) ?? null,
    unitPrice: unitPriceText(row.price) as string,
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
  /** What was typed before the person went back a step; the rows and the date start from it. */
  draft?: ItemsDraft | null
  onDraftChange?: (draft: ItemsDraft) => void
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
  draft,
  onDraftChange,
  pending,
  error,
  onSubmit,
}: ReceiptItemsStepProps) {
  const intl = useIntl()
  // The items on the shopping list are offered first: each one is a candidate (spec 6.7).
  const listItems = useShoppingList(ws).data
  const [issuedOn, setIssuedOn] = useState(draft?.issuedOn ?? defaultDate ?? '')
  const [rows, setRows] = useState<Row[]>(draft?.rows ?? [blankRow(0)])
  const [nextKey, setNextKey] = useState(draft?.nextKey ?? 1)
  // The messages show only after a try to save, then follow the rows as they are fixed.
  const [checked, setChecked] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  const prefix = useId()

  useEffect(() => {
    onDraftChange?.({ issuedOn, rows, nextKey })
  }, [issuedOn, rows, nextKey, onDraftChange])

  const problems = rows.map(problemsOf)
  const fieldId = (key: number, part: 'product' | 'quantity' | 'price') =>
    `${prefix}-${key}-${part}`
  const showing = (key: number, part: keyof RowProblems) =>
    checked && problems[rows.findIndex((row) => row.key === key)]?.[part] === true

  const change = (key: number, patch: Partial<Row>) =>
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  const choose = (key: number, product: ProductChoice | null) =>
    change(key, {
      product,
      ...(product?.kind === 'existing' && product.unit !== null && { unit: product.unit }),
    })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (pending) return
    const first = rows
      .flatMap((row, index) =>
        (['product', 'quantity', 'price'] as const)
          .filter((part) => problems[index]?.[part])
          .map((part) => fieldId(row.key, part)),
      )
      .at(0)
    if (first !== undefined) {
      setChecked(true)
      formRef.current?.querySelector<HTMLElement>(`[id="${first}"]`)?.focus()
      return
    }
    onSubmit({ issuedOn, items: rows.map(toItem) })
  }

  const message = (key: number, part: 'product' | 'quantity' | 'price') => {
    if (!showing(key, part)) return null
    const id = fieldId(key, part)
    const text = {
      product: 'markets.receipt.items.errorProduct',
      quantity: 'markets.receipt.items.errorQuantity',
      price: 'markets.receipt.items.errorPrice',
    }[part] as 'markets.receipt.items.errorProduct'
    return (
      <p id={`${id}-error`} className="text-sm text-negative">
        <FormattedMessage id={text} />
      </p>
    )
  }
  const aria = (key: number, part: 'product' | 'quantity' | 'price') =>
    showing(key, part)
      ? { 'aria-invalid': true as const, 'aria-describedby': `${fieldId(key, part)}-error` }
      : {}

  return (
    <form ref={formRef} onSubmit={submit} className="flex flex-col gap-4">
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
              text={row.search}
              onTextChange={(search) => change(row.key, { search })}
              listItems={listItems}
              inputId={fieldId(row.key, 'product')}
              invalid={showing(row.key, 'product')}
              describedBy={
                showing(row.key, 'product') ? `${fieldId(row.key, 'product')}-error` : undefined
              }
            />
            {message(row.key, 'product')}
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
                  id={fieldId(row.key, 'quantity')}
                  {...aria(row.key, 'quantity')}
                  type="text"
                  inputMode="decimal"
                  value={row.quantity}
                  onChange={(event) => change(row.key, { quantity: event.target.value })}
                  className={`${field} font-normal`}
                />
              </label>
            </div>
            {message(row.key, 'quantity')}
            <label className="flex flex-col gap-1 text-sm font-medium">
              <FormattedMessage id="markets.receipt.items.price" />
              <input
                id={fieldId(row.key, 'price')}
                {...aria(row.key, 'price')}
                type="text"
                inputMode="decimal"
                value={row.price}
                onChange={(event) => change(row.key, { price: event.target.value })}
                className={`${field} font-normal`}
              />
            </label>
            {message(row.key, 'price')}
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
      <button type="submit" disabled={pending} className={primary}>
        <FormattedMessage id="markets.receipt.items.save" />
      </button>
    </form>
  )
}
