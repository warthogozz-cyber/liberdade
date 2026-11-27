import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import type { ProductOverview } from '@/shared/api/markets'
import { useFormatMoney } from '@/shared/api/useFormatMoney'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { Sheet } from '@/shared/ui/Sheet'
import { marketsMessages } from '../messages.pt-BR'
import { unitPriceToCents } from '../price'
import { ProductPicker, type ProductChoice } from '../receipt/ProductPicker'
import { MAX_PRODUCT_NAME, PRODUCT_UNITS, type ProductUnit } from '../receipt/productUnits'
import { useWriteError } from '../useMarketWrites'
import { MergeDialog, type ProductName } from './MergeDialog'
import { SuggestionCard } from './SuggestionCard'
import {
  useDismissPair,
  useEditProduct,
  useProductsOverview,
  useProductSuggestions,
} from './useProducts'
import { PageTip } from '@/shared/help'

registerMessages(marketsMessages)

const FAMILY_ZONE = 'America/Sao_Paulo'
const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'

const asUnit = (unit: string) => unit as ProductUnit

interface EditProductSheetProps {
  ws: string
  product: ProductOverview
  onClose: () => void
  onMergeWith: (other: ProductName) => void
}

/** Renames a product or changes its unit, or starts joining it with another. */
function EditProductSheet({ ws, product, onClose, onMergeWith }: EditProductSheetProps) {
  const intl = useIntl()
  const edit = useEditProduct(ws)
  const error = useWriteError(edit)
  const [name, setName] = useState(product.name)
  const [unit, setUnit] = useState<ProductUnit>(asUnit(product.unit))
  const [joining, setJoining] = useState(false)
  const [picked, setPicked] = useState<ProductChoice | null>(null)
  const trimmed = name.trim()

  return (
    <Sheet
      open
      onOpenChange={(open) => !open && onClose()}
      title={<FormattedMessage id="markets.products.edit.title" />}
    >
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          if (trimmed === '') return
          edit.mutate(
            {
              id: product.id,
              body: {
                name: trimmed === product.name ? null : trimmed,
                unit: unit === product.unit ? null : unit,
              },
            },
            { onSuccess: onClose },
          )
        }}
      >
        <label className="flex flex-col gap-1 text-sm font-medium">
          <FormattedMessage id="markets.products.edit.name" />
          <input
            type="text"
            value={name}
            maxLength={MAX_PRODUCT_NAME}
            required
            onChange={(event) => setName(event.target.value)}
            className={`${field} font-normal`}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm font-medium">
          <FormattedMessage id="markets.products.edit.unit" />
          <select
            value={unit}
            onChange={(event) => setUnit(asUnit(event.target.value))}
            className={`${field} font-normal`}
          >
            {PRODUCT_UNITS.map((each) => (
              <option key={each} value={each}>
                {intl.formatMessage({ id: `markets.receipt.unit.${each}` })}
              </option>
            ))}
          </select>
        </label>
        {edit.isError && (
          <p role="alert" className="text-danger">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={edit.isPending || trimmed === ''}
          className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-50"
        >
          <FormattedMessage id="markets.products.edit.save" />
        </button>
      </form>
      <div className="mt-6 flex flex-col gap-3 border-t border-border pt-4">
        {joining ? (
          <ProductPicker
            ws={ws}
            value={picked}
            allowNew={false}
            onChange={(choice) => {
              if (choice?.kind === 'existing' && choice.id !== product.id) {
                onMergeWith({ id: choice.id, name: choice.name })
              } else {
                setPicked(null)
              }
            }}
          />
        ) : (
          <button type="button" onClick={() => setJoining(true)} className={outline}>
            <FormattedMessage id="markets.products.edit.merge" />
          </button>
        )}
      </div>
    </Sheet>
  )
}

/** The family's products with the last price at each market, and the pairs that may be one product. */
export function ProductsPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'markets.products.title' }))
  const formatMoney = useFormatMoney()
  const workspace = useCurrentWorkspace()
  const ws = workspace?.id
  // Everyone sees the suggestions; the buttons and the edits are for the people who may change products.
  const canWrite = workspace?.role === 'OWNER' || workspace?.role === 'ADULT'
  const overview = useProductsOverview(ws)
  const suggestions = useProductSuggestions(ws)
  const dismiss = useDismissPair(ws ?? '')
  const [editing, setEditing] = useState<ProductOverview | null>(null)
  const [merging, setMerging] = useState<{ a: ProductName; b: ProductName } | null>(null)

  const products = overview.data?.products
  const pairs = suggestions.data ?? []
  const formatDate = (day: string) =>
    intl.formatDate(`${day}T12:00:00Z`, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: FAMILY_ZONE,
    })

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <Link to="/mais/feira" className={`${outline} self-start`}>
        <FormattedMessage id="markets.products.back" />
      </Link>
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="markets.products.title" />
      </h1>
      <PageTip id="products" />

      {pairs.length > 0 && (
        <ul className="flex flex-col gap-3">
          {pairs.map((pair) => (
            <SuggestionCard
              key={`${pair.a.id}-${pair.b.id}`}
              pair={pair}
              busy={dismiss.isPending}
              canWrite={canWrite}
              onMerge={() => setMerging({ a: pair.a, b: pair.b })}
              onDismiss={() => dismiss.mutate({ a: pair.a.id, b: pair.b.id })}
            />
          ))}
        </ul>
      )}
      {dismiss.isError && (
        <p role="alert">
          <FormattedMessage id="markets.products.suggestion.failed" />
        </p>
      )}

      {overview.isError ? (
        <div className="flex flex-col gap-3">
          <p>
            <FormattedMessage id="markets.products.loadFailed" />
          </p>
          <button type="button" onClick={() => void overview.refetch()} className={outline}>
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : products === undefined ? (
        <p role="status">
          <FormattedMessage id="common.loading" />
        </p>
      ) : products.length === 0 ? (
        <p className="text-text-muted">
          <FormattedMessage id="markets.products.empty" />
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {products.map((product) => (
            <li key={product.id} className="flex flex-col gap-2 rounded-card bg-surface px-4 py-3">
              <div className="flex items-baseline justify-between gap-2">
                <span className="min-w-0">
                  <span className="font-medium">{product.name}</span>{' '}
                  <span className="text-sm text-text-muted">
                    {intl.formatMessage({
                      id: `markets.receipt.unit.${asUnit(product.unit)}`,
                      defaultMessage: product.unit,
                    })}
                  </span>
                </span>
                {canWrite && (
                  <button
                    type="button"
                    aria-label={intl.formatMessage(
                      { id: 'markets.products.edit' },
                      { name: product.name },
                    )}
                    onClick={() => setEditing(product)}
                    className={`min-h-12 px-3 font-medium text-primary underline ${focus}`}
                  >
                    <span aria-hidden="true">✎</span>
                  </button>
                )}
              </div>
              {product.prices.length === 0 ? (
                <p className="text-sm text-text-muted">
                  <FormattedMessage id="markets.products.noPrice" />
                </p>
              ) : (
                <ul className="flex flex-col gap-1">
                  {product.prices.map((price) => (
                    <li
                      key={price.marketRef}
                      className="flex flex-wrap items-baseline justify-between gap-x-3 text-sm"
                    >
                      <span>
                        {price.marketName ?? intl.formatMessage({ id: 'markets.left.unknown' })}
                      </span>
                      <span>
                        <FormattedMessage
                          id="markets.prices.line"
                          values={{
                            price: formatMoney(unitPriceToCents(price.unitPrice)),
                            date: formatDate(price.observedOn),
                          }}
                        />
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
      {overview.data?.truncated === true && (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.products.truncated" />
        </p>
      )}

      {editing !== null && ws !== undefined && (
        <EditProductSheet
          ws={ws}
          product={editing}
          onClose={() => setEditing(null)}
          onMergeWith={(other) => {
            setMerging({ a: { id: editing.id, name: editing.name }, b: other })
            setEditing(null)
          }}
        />
      )}
      {merging !== null && ws !== undefined && (
        <MergeDialog
          ws={ws}
          a={merging.a}
          b={merging.b}
          onClose={() => setMerging(null)}
          onMerged={() => setMerging(null)}
        />
      )}
    </main>
  )
}
