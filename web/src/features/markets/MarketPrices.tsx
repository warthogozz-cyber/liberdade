import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { registerMessages } from '@/shared/i18n'
import { useFormatMoney } from '@/shared/api/useFormatMoney'
import { marketsMessages } from './messages.pt-BR'
import { unitPriceToCents } from './price'
import type { ProductUnit } from './receipt/productUnits'
import { useMarketPrices } from './useReceipts'

registerMessages(marketsMessages)

const FAMILY_ZONE = 'America/Sao_Paulo'
const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

interface MarketPricesProps {
  ws: string
  marketRef: string
  /** Owner and Adult: the ones who may register a receipt. */
  canRegister: boolean
}

/** The last price of each product at one market, with the day it was seen. */
export function MarketPrices({ ws, marketRef, canRegister }: MarketPricesProps) {
  const intl = useIntl()
  const formatMoney = useFormatMoney()
  const query = useMarketPrices(ws, marketRef)
  const prices = query.data

  return (
    <section className="flex flex-col gap-3">
      <h2 className="font-medium">
        <FormattedMessage id="markets.prices.title" />
      </h2>
      {query.isError ? (
        <p>
          <FormattedMessage id="markets.prices.loadFailed" />
        </p>
      ) : prices === undefined ? (
        <p role="status">
          <FormattedMessage id="common.loading" />
        </p>
      ) : prices.length === 0 ? (
        <p className="text-text-muted">
          <FormattedMessage id="markets.prices.empty" />
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {prices.map((price) => (
            <li
              key={price.productId}
              className="flex flex-col gap-1 rounded-card bg-surface px-4 py-3"
            >
              <span className="flex flex-wrap items-baseline justify-between gap-x-2">
                <span className="font-medium">{price.name}</span>
                <span className="text-sm text-text-muted">
                  {intl.formatMessage({
                    id: `markets.receipt.unit.${price.unit as ProductUnit}`,
                    defaultMessage: price.unit,
                  })}
                </span>
              </span>
              <span>
                <FormattedMessage
                  id="markets.prices.line"
                  values={{
                    price: formatMoney(unitPriceToCents(price.unitPrice)),
                    date: intl.formatDate(`${price.observedOn}T12:00:00Z`, {
                      day: '2-digit',
                      month: '2-digit',
                      year: 'numeric',
                      timeZone: FAMILY_ZONE,
                    }),
                  }}
                />
              </span>
            </li>
          ))}
        </ul>
      )}
      {canRegister && (
        <Link
          to="/mais/feira/nota"
          className={`flex min-h-12 items-center justify-center self-start rounded-field border border-primary px-4 font-medium text-primary ${focus}`}
        >
          <FormattedMessage id="markets.receipt.register" />
        </Link>
      )}
    </section>
  )
}
