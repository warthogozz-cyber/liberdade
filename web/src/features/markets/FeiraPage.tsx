import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate } from 'react-router'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { useMarketHome, useMarketsSettings, type MarketSearchResult } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { AddMarketSheet } from './AddMarketSheet'
import { distanceMeters, type GeoPoint } from './geo'
import { LocationChooser } from './LocationChooser'
import { readLocation, type StoredLocation } from './locationStore'
import { MarketRow, type MarketListItem } from './MarketRow'
import { useKindLabel } from './useKindLabel'
import { marketsMessages } from './messages.pt-BR'
import { OsmAttribution } from './OsmAttribution'
import { DEFAULT_RADIUS_KM, useMarketSearch } from './useMarkets'

// The text loads with this lazy screen, not with the app.
registerMessages(marketsMessages)

/** The most markets the list shows. */
const MAX_MARKETS = 50

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

const toPoint = (lat: string, lon: string): GeoPoint => ({ lat: Number(lat), lon: Number(lon) })

/** OpenStreetMap's and the family's markets together, measured, nearest first, inside the radius. */
function listMarkets(
  result: MarketSearchResult,
  from: GeoPoint,
  radiusKm: number,
): MarketListItem[] {
  const osm: MarketListItem[] = result.osm.map((each) => ({
    ref: each.ref,
    familyId: each.familyId,
    name: each.name,
    kind: each.kind,
    address: osmAddress(each),
    fromFamily: false,
    meters: distanceMeters(from, toPoint(each.lat, each.lon)),
  }))
  const family: MarketListItem[] = result.family.map((each) => ({
    ref: each.ref,
    name: each.name,
    kind: each.kind,
    address: null,
    fromFamily: true,
    meters: distanceMeters(from, toPoint(each.lat, each.lon)),
  }))
  return [...osm, ...family]
    .filter((each) => each.meters <= radiusKm * 1000)
    .sort((a, b) => a.meters - b.meters)
    .slice(0, MAX_MARKETS)
}

function osmAddress(item: MarketSearchResult['osm'][number]): string | null {
  const street = [item.street, item.housenumber].filter(Boolean).join(', ')
  return street || item.suburb || item.city || null
}

/** The nearby markets: where the person looks is theirs to choose, and the data's credit stays on screen. */
export function FeiraPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'markets.title' }))
  const workspace = useCurrentWorkspace()
  const ws = workspace?.id
  const userId = useMe().data?.user.id
  // The family's address is for the people who may change it (the API refuses the others).
  const canWrite = workspace?.role === 'OWNER' || workspace?.role === 'ADULT'
  const settings = useMarketsSettings(ws)
  const home = useMarketHome(ws, canWrite)
  const kindLabel = useKindLabel()
  const navigate = useNavigate()

  const [chosen, setChosen] = useState<StoredLocation | null>(null)
  // The precise position lives in this screen's memory only: it is never saved or sent.
  const [precise, setPrecise] = useState<GeoPoint | null>(null)
  const [opened, setOpened] = useState(false)
  const [dismissed, setDismissed] = useState(false)
  const [adding, setAdding] = useState(false)

  const location = chosen ?? (userId !== undefined ? readLocation(userId) : null)
  const approximate = location?.mode === 'approximate'
  const homePoint = home.data ? toPoint(home.data.lat, home.data.lon) : null
  const from: GeoPoint | null =
    location === null
      ? null
      : location.mode === 'precise'
        ? precise
        : location.mode === 'home'
          ? homePoint
          : location.point
            ? toPoint(location.point.lat, location.point.lon)
            : null

  const search = useMarketSearch(ws, from)
  const markets = search.data && from ? listMarkets(search.data, from, DEFAULT_RADIUS_KM) : []

  // Asks where to look the first time, and again when the saved choice has no point to measure from.
  const ready = userId !== undefined && !settings.isPending && (!canWrite || !home.isPending)
  const chooserOpen = opened || (ready && from === null && !dismissed)

  const modeName =
    location === null || from === null
      ? intl.formatMessage({ id: 'markets.mode.choose' })
      : location.mode === 'typed' && location.point?.label
        ? location.point.label
        : intl.formatMessage({ id: `markets.mode.${location.mode}` })

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">
          <FormattedMessage id="markets.title" />
        </h1>
        <button
          type="button"
          aria-haspopup="dialog"
          aria-label={intl.formatMessage({ id: 'markets.mode.label' }, { mode: modeName })}
          onClick={() => setOpened(true)}
          className={`${outline} min-w-0`}
        >
          <span aria-hidden="true" className="truncate">
            📍 {modeName} ▾
          </span>
        </button>
      </div>

      {search.data !== undefined ? (
        <>
          {markets.length === 0 ? (
            <p className="text-text-muted">
              <FormattedMessage id="markets.empty" />
            </p>
          ) : (
            <ul className="flex flex-col gap-3">
              {markets.map((market) => (
                <MarketRow key={market.ref} market={market} approximate={approximate} />
              ))}
            </ul>
          )}
          {search.data.unlocated.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="font-medium">
                <FormattedMessage id="markets.unlocated" />
              </h2>
              <ul className="flex flex-col gap-2">
                {search.data.unlocated.map((each) => (
                  <li key={each.ref} className="rounded-card bg-surface">
                    <Link
                      to={`/mais/feira/mercado/${each.ref}`}
                      className={`flex min-h-12 flex-col justify-center px-4 py-2 ${focus}`}
                    >
                      <span className="font-medium">{each.name}</span>
                      <span className="text-sm text-text-muted">{kindLabel(each.kind)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {search.data.left.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="font-medium">
                <FormattedMessage id="markets.left" />
              </h2>
              <ul className="flex flex-col gap-2">
                {search.data.left.map((each) => (
                  <li key={each.ref} className="rounded-card bg-surface">
                    <Link
                      to={`/mais/feira/mercado/${each.ref}`}
                      className={`flex min-h-12 items-center px-4 py-2 ${focus}`}
                    >
                      <FormattedMessage
                        id={each.cnpj ? 'markets.left.cnpj' : 'markets.left.unknown'}
                        values={{ cnpj: each.cnpj }}
                      />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      ) : search.isError ? (
        <div className="flex flex-col items-start gap-2">
          <p>
            <FormattedMessage id="markets.loadFailed" />
          </p>
          <button type="button" onClick={() => void search.refetch()} className={outline}>
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : from !== null ? (
        <ul
          aria-busy="true"
          aria-label={intl.formatMessage({ id: 'markets.loading' })}
          className="flex flex-col gap-3"
        >
          {[0, 1, 2].map((slot) => (
            <li key={slot} className="h-20 rounded-card bg-surface" />
          ))}
        </ul>
      ) : null}

      {canWrite && (
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={() => setAdding(true)} className={outline}>
            <FormattedMessage id="markets.add" />
          </button>
          <Link to="/mais/feira/nota" className={outline}>
            <FormattedMessage id="markets.receipt.register" />
          </Link>
        </div>
      )}

      <OsmAttribution date={search.data?.datasetDate ?? null} />

      {adding && ws !== undefined && (
        <AddMarketSheet
          open
          onOpenChange={(open) => !open && setAdding(false)}
          workspaceId={ws}
          geocodingEnabled={settings.data?.geocodingEnabled === true}
          onCreated={(card) => navigate(`/mais/feira/mercado/${card.familyId ?? card.ref}`)}
        />
      )}

      {chooserOpen && userId !== undefined && ws !== undefined && (
        <LocationChooser
          open
          onOpenChange={(open) => {
            if (!open) {
              setOpened(false)
              setDismissed(true)
            }
          }}
          userId={userId}
          workspaceId={ws}
          current={location}
          geocodingEnabled={settings.data?.geocodingEnabled === true}
          homeAvailable={canWrite && homePoint !== null}
          onChosen={(next, point) => {
            setChosen(next)
            setPrecise(point)
            setOpened(false)
            setDismissed(false)
          }}
        />
      )}
    </main>
  )
}
