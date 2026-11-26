import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import type { MarketKind } from '@/shared/api/markets'
import { useKindLabel } from './useKindLabel'
import { formatDistance } from './geo'

/** A market ready for the list: from either list of the search, measured from where the person is. */
export type MarketListItem = {
  ref: string
  /** The family's row of this market, when it has one: the card opens by it (D4). */
  familyId?: string | null
  name: string | null
  kind: MarketKind | null
  /** The street and number, from OpenStreetMap; absent for the family's own markets. */
  address: string | null
  fromFamily: boolean
  meters: number
}

/** One line of the list: the name (or "<Tipo> sem nome"), the distance, the kind and where it is. */
export function MarketRow({
  market,
  approximate,
}: {
  market: MarketListItem
  approximate: boolean
}) {
  const intl = useIntl()
  const kindLabel = useKindLabel()(market.kind)
  const name = market.name ?? intl.formatMessage({ id: 'markets.unnamed' }, { kind: kindLabel })
  const detail = market.fromFamily ? intl.formatMessage({ id: 'markets.byFamily' }) : market.address
  return (
    <li className="rounded-card bg-surface">
      <Link
        to={`/mais/feira/mercado/${market.familyId ?? market.ref}`}
        className="flex flex-col gap-1 p-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <span className="flex items-baseline justify-between gap-2">
          <span className="font-medium">{name}</span>
          <span className="shrink-0 text-sm text-text-muted">
            {formatDistance(market.meters, approximate)}
          </span>
        </span>
        <span className="text-sm text-text-muted">
          {detail ? `${kindLabel} · ${detail}` : kindLabel}
        </span>
      </Link>
    </li>
  )
}
