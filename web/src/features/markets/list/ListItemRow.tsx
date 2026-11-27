import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { ShoppingListItem } from '@/shared/api/markets'
import { useFormatMoney } from '@/shared/api/useFormatMoney'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from '../messages.pt-BR'
import { unitPriceToCents } from '../price'
import { parseQuantity, showQuantity, stepQuantity } from './quantity'

registerMessages(marketsMessages)

const FAMILY_ZONE = 'America/Sao_Paulo'
const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const small = `flex min-h-12 min-w-12 items-center justify-center rounded-field border border-primary px-3 font-medium text-primary disabled:opacity-50 ${focus}`
const field = 'min-h-12 w-20 rounded-field border border-text-muted bg-background px-3 text-base'

interface ListItemRowProps {
  item: ShoppingListItem
  /** Who may mark, change the quantity and move: everyone but the viewer. */
  canEdit: boolean
  /** Who may take the item off the list: the owner and the adults. */
  canRemove: boolean
  isFirst: boolean
  isLast: boolean
  onCheck: (checked: boolean) => void
  /** The new quantity, as the API's decimal text. */
  onQuantity: (quantity: string) => void
  onMove: (direction: 'up' | 'down') => void
  onRemove: () => void
}

/** One item of the shopping list: the box, the name, the quantity and the last price at each market. */
export function ListItemRow({
  item,
  canEdit,
  canRemove,
  isFirst,
  isLast,
  onCheck,
  onQuantity,
  onMove,
  onRemove,
}: ListItemRowProps) {
  const intl = useIntl()
  const formatMoney = useFormatMoney()
  // What is being typed in the quantity field; null while it just shows the item's own quantity.
  const [typed, setTyped] = useState<string | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const { name } = item
  const formatDate = (day: string) =>
    intl.formatDate(`${day}T12:00:00Z`, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: FAMILY_ZONE,
    })
  const unit = intl.formatMessage({
    id: `markets.receipt.unit.${item.unit}`,
    defaultMessage: item.unit,
  })

  function commit() {
    if (typed === null) return
    const quantity = parseQuantity(typed)
    setTyped(null)
    if (quantity !== null && quantity !== item.quantity) onQuantity(quantity)
  }
  const step = (by: 1 | -1) => {
    const next = stepQuantity(item.quantity, by)
    if (next !== null) onQuantity(next)
  }
  const pick = (action: () => void) => () => {
    setMenuOpen(false)
    action()
  }

  return (
    <li className="flex flex-col gap-2 rounded-card bg-surface px-4 py-3">
      <div className="flex items-center gap-3">
        <input
          type="checkbox"
          checked={item.checked}
          disabled={!canEdit}
          aria-label={intl.formatMessage({ id: 'markets.list.check' }, { name })}
          onChange={(event) => onCheck(event.target.checked)}
          className={`size-6 shrink-0 ${focus}`}
        />
        <span className="min-w-0 flex-1">
          <span className={`font-medium ${item.checked ? 'line-through text-text-muted' : ''}`}>
            {name}
          </span>{' '}
          <span className="text-sm text-text-muted">{unit}</span>
        </span>
        {canEdit ? (
          <button
            type="button"
            aria-expanded={menuOpen}
            aria-label={intl.formatMessage({ id: 'markets.list.menu' }, { name })}
            onClick={() => setMenuOpen((open) => !open)}
            className={small}
          >
            <span aria-hidden="true">⋯</span>
          </button>
        ) : (
          <span className="font-medium">{showQuantity(item.quantity)}</span>
        )}
      </div>

      {canEdit && (
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={stepQuantity(item.quantity, -1) === null}
            aria-label={intl.formatMessage({ id: 'markets.list.quantity.less' }, { name })}
            onClick={() => step(-1)}
            className={small}
          >
            <span aria-hidden="true">−</span>
          </button>
          <input
            type="text"
            inputMode="decimal"
            autoComplete="off"
            value={typed ?? showQuantity(item.quantity)}
            aria-label={intl.formatMessage({ id: 'markets.list.quantity' }, { name })}
            onChange={(event) => setTyped(event.target.value)}
            onBlur={commit}
            onKeyDown={(event) => event.key === 'Enter' && commit()}
            className={`${field} text-center`}
          />
          <button
            type="button"
            disabled={stepQuantity(item.quantity, 1) === null}
            aria-label={intl.formatMessage({ id: 'markets.list.quantity.more' }, { name })}
            onClick={() => step(1)}
            className={small}
          >
            <span aria-hidden="true">+</span>
          </button>
        </div>
      )}

      {menuOpen && (
        <div className="flex flex-wrap gap-2">
          {!item.checked && (
            <>
              <button
                type="button"
                disabled={isFirst}
                onClick={pick(() => onMove('up'))}
                className={small}
              >
                <FormattedMessage id="markets.list.up" />
              </button>
              <button
                type="button"
                disabled={isLast}
                onClick={pick(() => onMove('down'))}
                className={small}
              >
                <FormattedMessage id="markets.list.down" />
              </button>
            </>
          )}
          {canRemove && (
            <button type="button" onClick={pick(onRemove)} className={small}>
              <FormattedMessage id="markets.list.remove" />
            </button>
          )}
        </div>
      )}

      {item.prices.length === 0 ? (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.list.noPrice" />
        </p>
      ) : (
        <ul className="flex flex-col gap-1 text-sm">
          {item.prices.map((price) => (
            <li key={price.marketRef}>
              <FormattedMessage
                id="markets.list.price"
                values={{
                  market: price.marketName ?? intl.formatMessage({ id: 'markets.left.unknown' }),
                  price: formatMoney(unitPriceToCents(price.unitPrice)),
                  date: formatDate(price.observedOn),
                }}
              />
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
