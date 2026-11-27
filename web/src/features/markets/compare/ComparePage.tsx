import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import type { ComparedItem, MarketComparison } from '@/shared/api/markets'
import { useFormatMoney } from '@/shared/api/useFormatMoney'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { marketsMessages } from '../messages.pt-BR'
import { unitPriceToCents } from '../price'
import { useShoppingList } from '../list/useShoppingList'
import { useCompare } from './useCompare'
import { PageTip } from '@/shared/help'

registerMessages(marketsMessages)

const FAMILY_ZONE = 'America/Sao_Paulo'
const MAX_MARKETS = 3
const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

/**
 * The shopping list priced at two or three markets, side by side. Nothing here ranks the markets:
 * the totals come in the order the person chose, all alike, and each price carries its date.
 */
export function ComparePage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'markets.compare.title' }))
  const formatMoney = useFormatMoney()
  const ws = useCurrentWorkspace()?.id
  const list = useShoppingList(ws)
  // Until the person chooses, the two markets the API suggests are the choice.
  const suggestions = useCompare(ws, [])
  const [chosen, setChosen] = useState<string[] | null>(null)
  const refs = chosen ?? suggestions.data?.suggested ?? []
  const comparison = useCompare(ws, refs)

  // The markets that price something on the list, as the list itself shows them.
  const unknown = intl.formatMessage({ id: 'markets.left.unknown' })
  const candidates = new Map<string, string>()
  for (const item of list.data ?? []) {
    for (const price of item.prices) {
      if (!candidates.has(price.marketRef))
        candidates.set(price.marketRef, price.marketName ?? unknown)
    }
  }

  const toggle = (ref: string) =>
    setChosen(refs.includes(ref) ? refs.filter((each) => each !== ref) : [...refs, ref])

  const formatDate = (day: string) =>
    intl.formatDate(`${day}T12:00:00Z`, { day: '2-digit', month: '2-digit', timeZone: FAMILY_ZONE })
  const money = (total: string) => formatMoney(unitPriceToCents(total))

  const failed = list.isError || suggestions.isError || comparison.isError
  const loading = list.isPending || suggestions.isPending

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <Link to="/mais/feira/lista" className={`${outline} self-start`}>
        <FormattedMessage id="markets.compare.back" />
      </Link>
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="markets.compare.title" />
      </h1>
      <PageTip id="compare" />

      {failed ? (
        <p role="alert">
          <FormattedMessage id="markets.compare.loadFailed" />
        </p>
      ) : loading ? (
        <p role="status">
          <FormattedMessage id="common.loading" />
        </p>
      ) : candidates.size === 0 ? (
        <p className="text-text-muted">
          <FormattedMessage id="markets.compare.noMarkets" />
        </p>
      ) : (
        <>
          <fieldset className="flex flex-col gap-1">
            <legend className="mb-1 font-medium">
              <FormattedMessage id="markets.compare.choose" />
            </legend>
            {[...candidates].map(([ref, name]) => (
              <label key={ref} className="flex min-h-12 items-center gap-3">
                <input
                  type="checkbox"
                  className="size-5"
                  checked={refs.includes(ref)}
                  disabled={!refs.includes(ref) && refs.length >= MAX_MARKETS}
                  onChange={() => toggle(ref)}
                />
                {name}
              </label>
            ))}
          </fieldset>

          {refs.length < 2 ? (
            <p className="text-text-muted">
              <FormattedMessage id="markets.compare.atLeastTwo" />
            </p>
          ) : comparison.data === undefined ? (
            <p role="status">
              <FormattedMessage id="common.loading" />
            </p>
          ) : (
            <Result
              comparison={comparison.data}
              unknown={unknown}
              money={money}
              formatDate={formatDate}
            />
          )}
        </>
      )}
    </main>
  )
}

interface ResultProps {
  comparison: MarketComparison
  unknown: string
  money: (total: string) => string
  formatDate: (day: string) => string
}

/** One row per item, one line per market, and the footer with the counts and the totals. */
function Result({ comparison, unknown, money, formatDate }: ResultProps) {
  const intl = useIntl()
  const nameOf = (index: number) => comparison.markets[index]?.name ?? unknown
  const line = (item: ComparedItem, index: number) => {
    const price = item.prices[index] ?? null
    return (
      <li key={comparison.markets[index]?.ref ?? index} className="flex flex-wrap gap-x-2">
        <span className="text-text-muted">{nameOf(index)}</span>
        {price === null ? (
          <span>{intl.formatMessage({ id: 'markets.compare.noPrice' })}</span>
        ) : (
          <>
            <span>
              {intl.formatMessage(
                { id: 'markets.compare.price' },
                { price: money(price.unitPrice), date: formatDate(price.observedOn) },
              )}
            </span>
            {price.old && <span>{intl.formatMessage({ id: 'markets.compare.old' })}</span>}
          </>
        )}
      </li>
    )
  }

  return (
    <>
      <ul className="flex flex-col gap-3">
        {comparison.items.map((item) => (
          <li
            key={item.productId}
            className="flex flex-col gap-1 rounded-card border border-border p-3"
          >
            <span className="font-medium">{item.name}</span>
            <ul className="flex flex-col gap-1 text-sm">
              {comparison.markets.map((_, index) => line(item, index))}
            </ul>
          </li>
        ))}
      </ul>

      <section className="flex flex-col gap-2">
        <p className="font-medium">
          <FormattedMessage id="markets.compare.common" values={{ n: comparison.commonCount }} />
        </p>
        <ul
          aria-label={intl.formatMessage({ id: 'markets.compare.totals' })}
          className="flex flex-col gap-1"
        >
          {comparison.markets.map((market, index) => (
            <li key={market.ref} className="flex justify-between gap-3">
              <span>{nameOf(index)}</span>
              <span>{money(market.total)}</span>
            </li>
          ))}
        </ul>
        {comparison.markets
          .filter((market) => market.missingCount > 0)
          .map((market) => (
            <p key={market.ref} className="text-sm">
              <FormattedMessage
                id="markets.compare.missing"
                values={{ k: market.missingCount, market: market.name ?? unknown }}
              />
            </p>
          ))}
        {comparison.unitMismatchCount > 0 && (
          <p className="text-sm">
            <FormattedMessage
              id="markets.compare.mismatch"
              values={{ u: comparison.unitMismatchCount }}
            />
          </p>
        )}
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.compare.source" />
        </p>
      </section>
    </>
  )
}
