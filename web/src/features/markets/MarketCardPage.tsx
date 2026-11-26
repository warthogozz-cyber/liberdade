import { useState, type ReactNode } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate, useParams } from 'react-router'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { useMarketHome, useMarketsSettings, type MarketCard } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { SourceStamp } from '@/shared/ui/SourceStamp'
import { ConvertMarketSheet } from './ConvertMarketSheet'
import { distanceMeters, formatDistance, type GeoPoint } from './geo'
import { GiveLocationSheet } from './GiveLocationSheet'
import { InstagramEmbed } from './InstagramEmbed'
import { isSafeLink, mapsDirectionsUrl, mapsSearchUrl, wazeUrl } from './links'
import { LinksSheet } from './LinksSheet'
import { readLocation } from './locationStore'
import { MapEmbed } from './MapEmbed'
import { marketsMessages } from './messages.pt-BR'
import { OsmAttribution } from './OsmAttribution'
import { useKindLabel } from './useKindLabel'
import { useArchiveMarket } from './useMarketWrites'
import { useMarketCard } from './useMarkets'

registerMessages(marketsMessages)

const FAMILY_ZONE = 'America/Sao_Paulo'
const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-60 ${focus}`
const anchor = `font-medium text-primary underline ${focus}`

/** A calendar day (`2026-01-01`) as an instant that stays on that day in São Paulo's calendar. */
const dayInstant = (day: string | null) => (day === null ? null : `${day}T12:00:00Z`)

/** Today's date in São Paulo's calendar, as `yyyy-mm-dd`. */
function todayInSaoPaulo(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: FAMILY_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(Date.now())
}

function ExternalLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className={anchor}>
      {children}
    </a>
  )
}

function address(card: MarketCard): string | null {
  const place = card.address
  if (place === null) return null
  const street = [place.street, place.housenumber].filter(Boolean).join(', ')
  return [street, place.suburb, place.city].filter(Boolean).join(' · ') || null
}

/** One market's card: what is known of it and where each piece came from. */
export function MarketCardPage() {
  const intl = useIntl()
  const { ref } = useParams()
  const navigate = useNavigate()
  const workspace = useCurrentWorkspace()
  const ws = workspace?.id
  const userId = useMe().data?.user.id
  const canWrite = workspace?.role === 'OWNER' || workspace?.role === 'ADULT'
  const query = useMarketCard(ws, ref)
  const settings = useMarketsSettings(ws)
  const home = useMarketHome(ws, canWrite)
  const kindLabel = useKindLabel()
  const archive = useArchiveMarket(ws ?? '', ref ?? '')
  const [sheet, setSheet] = useState<'links' | 'convert' | 'locate' | null>(null)
  const card = query.data
  const name = card
    ? (card.name ?? intl.formatMessage({ id: 'markets.unnamed' }, { kind: kindLabel(card.kind) }))
    : ''
  useDocumentTitle(name || intl.formatMessage({ id: 'markets.title' }))

  if (query.isError) {
    return (
      <main className="mx-auto flex max-w-lg flex-col items-start gap-3 p-6">
        <p>
          <FormattedMessage id="markets.card.loadFailed" />
        </p>
        <button type="button" onClick={() => void query.refetch()} className={outline}>
          <FormattedMessage id="common.retry" />
        </button>
        <Link to="/mais/feira" className={anchor}>
          <FormattedMessage id="markets.card.back" />
        </Link>
      </main>
    )
  }
  if (card === undefined || ws === undefined || ref === undefined) {
    return (
      <main className="mx-auto max-w-lg p-6">
        <p role="status">
          <FormattedMessage id="common.loading" />
        </p>
      </main>
    )
  }

  const point: GeoPoint | null =
    card.lat !== null && card.lon !== null ? { lat: Number(card.lat), lon: Number(card.lon) } : null
  // The distance comes from the position kept on the device; a precise one is never kept.
  const location = userId !== undefined ? readLocation(userId) : null
  const from: GeoPoint | null =
    location === null || location.mode === 'precise'
      ? null
      : location.mode === 'home'
        ? home.data
          ? { lat: Number(home.data.lat), lon: Number(home.data.lon) }
          : null
        : location.point
          ? { lat: Number(location.point.lat), lon: Number(location.point.lon) }
          : null
  const distance =
    point !== null && from !== null
      ? formatDistance(distanceMeters(from, point), location?.mode === 'approximate')
      : null

  const hasLinks = card.website !== null || card.instagram !== null || card.flyer !== null
  const flyerExpired = card.flyer?.validUntil != null && card.flyer.validUntil < todayInSaoPaulo()
  const archived = card.status === 'ARCHIVED'
  const geocodingEnabled = settings.data?.geocodingEnabled === true
  const hours = card.openingHours
  const place = address(card)

  const stamp = (link: NonNullable<MarketCard['website']>) =>
    link.source === 'FAMILY' ? (
      <SourceStamp
        source={intl.formatMessage({ id: 'markets.card.byFamily' })}
        updatedAt={dayInstant(link.date)}
        stale={false}
      />
    ) : (
      <SourceStamp
        source={intl.formatMessage({ id: 'markets.card.osm' })}
        updatedAt={dayInstant(link.date ?? card.datasetDate)}
        stale={false}
      />
    )

  const doArchive = () =>
    archive.mutate(undefined, { onSuccess: () => navigate('/mais/feira', { replace: true }) })

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <Link to="/mais/feira" className={`${anchor} self-start`}>
        <FormattedMessage id="markets.card.back" />
      </Link>

      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">{name}</h1>
        <p className="text-text-muted">
          {[kindLabel(card.kind), place, distance].filter(Boolean).join(' · ')}
        </p>
        {hours !== null && (
          <p className="text-sm text-text-muted">
            <FormattedMessage id="markets.card.hours" values={{ hours }} />
          </p>
        )}
        {card.cnpj !== null && (
          <p className="text-sm text-text-muted">
            <FormattedMessage id="markets.card.cnpj" values={{ cnpj: card.cnpj }} />
          </p>
        )}
        {archived && (
          <p role="status">
            <FormattedMessage id="markets.card.archived" />
          </p>
        )}
        {card.status === 'LEFT_OSM' && (
          <p role="status">
            <FormattedMessage id="markets.card.leftOsm" />
          </p>
        )}
      </header>

      {point !== null && (
        <ul className="flex flex-wrap gap-4">
          <li>
            <ExternalLink href={mapsSearchUrl(card.lat as string, card.lon as string)}>
              <FormattedMessage id="markets.card.maps" />
            </ExternalLink>
          </li>
          <li>
            <ExternalLink href={mapsDirectionsUrl(card.lat as string, card.lon as string)}>
              <FormattedMessage id="markets.card.directions" />
            </ExternalLink>
          </li>
          <li>
            <ExternalLink href={wazeUrl(card.lat as string, card.lon as string)}>
              <FormattedMessage id="markets.card.waze" />
            </ExternalLink>
          </li>
        </ul>
      )}

      {point !== null && (
        <MapEmbed userId={userId} name={name} lat={card.lat as string} lon={card.lon as string} />
      )}

      <section className="flex flex-col gap-3">
        <h2 className="font-medium">
          <FormattedMessage id="markets.card.links" />
        </h2>
        {!hasLinks && (
          <p className="text-text-muted">
            <FormattedMessage id="markets.card.noLinks" />
          </p>
        )}
        {card.website !== null && (
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium">
              <FormattedMessage id="markets.card.website" />
            </span>
            {isSafeLink(card.website.value) ? (
              <ExternalLink href={card.website.value}>{card.website.value}</ExternalLink>
            ) : (
              <span>{card.website.value}</span>
            )}
            {stamp(card.website)}
          </div>
        )}
        {card.instagram !== null && (
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium">
              <FormattedMessage id="markets.card.instagram" />
            </span>
            {isSafeLink(card.instagram.value) ? (
              <ExternalLink href={card.instagram.value}>{card.instagram.value}</ExternalLink>
            ) : (
              <span>{card.instagram.value}</span>
            )}
            {stamp(card.instagram)}
          </div>
        )}
        {card.flyer !== null && (
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium">
              <FormattedMessage id="markets.card.flyer" />
            </span>
            {isSafeLink(card.flyer.url) ? (
              <ExternalLink href={card.flyer.url}>{card.flyer.url}</ExternalLink>
            ) : (
              <span>{card.flyer.url}</span>
            )}
            {card.flyer.source !== null && (
              <span className="text-sm text-text-muted">
                <FormattedMessage
                  id="markets.card.flyerSource"
                  values={{ source: card.flyer.source }}
                />
              </span>
            )}
            {flyerExpired ? (
              <span className="text-sm font-medium">
                <FormattedMessage id="markets.card.flyerExpired" />
              </span>
            ) : (
              card.flyer.validUntil != null && (
                <span className="text-sm text-text-muted">
                  <FormattedMessage
                    id="markets.card.flyerUntil"
                    values={{
                      date: intl.formatDate(`${card.flyer.validUntil}T12:00:00Z`, {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric',
                        timeZone: FAMILY_ZONE,
                      }),
                    }}
                  />
                </span>
              )
            )}
            <SourceStamp
              source={intl.formatMessage({ id: 'markets.card.byFamily' })}
              updatedAt={card.flyer.setAt}
              stale={false}
            />
          </div>
        )}
        {card.instagramEmbeddable &&
          settings.data?.instagramEnabled === true &&
          card.instagram !== null && (
            <InstagramEmbed
              userId={userId}
              workspaceId={ws}
              marketRef={ref}
              url={card.instagram.value}
            />
          )}
      </section>

      {canWrite && !archived && (
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={() => setSheet('links')} className={outline}>
            <FormattedMessage id="markets.card.editLinks" />
          </button>
          {card.origin === 'FAMILY' && point === null && (
            <button type="button" onClick={() => setSheet('locate')} className={outline}>
              <FormattedMessage id="markets.card.giveLocation" />
            </button>
          )}
          {card.status === 'LEFT_OSM' && (
            <button type="button" onClick={() => setSheet('convert')} className={outline}>
              <FormattedMessage id="markets.convert.open" />
            </button>
          )}
          {(card.status === 'LEFT_OSM' || card.origin === 'FAMILY') && (
            <button
              type="button"
              onClick={doArchive}
              disabled={archive.isPending}
              className={outline}
            >
              <FormattedMessage id="markets.archive" />
            </button>
          )}
        </div>
      )}
      {archive.isError && (
        <p role="alert">
          <FormattedMessage id="markets.archive.failed" />
        </p>
      )}

      {card.origin === 'OSM' || card.datasetDate !== null ? (
        <OsmAttribution date={card.datasetDate} />
      ) : null}

      {sheet === 'links' && (
        <LinksSheet
          open
          onOpenChange={(open) => !open && setSheet(null)}
          workspaceId={ws}
          marketRef={ref}
          card={card}
        />
      )}
      {sheet === 'locate' && (
        <GiveLocationSheet
          open
          onOpenChange={(open) => !open && setSheet(null)}
          workspaceId={ws}
          marketRef={ref}
          geocodingEnabled={geocodingEnabled}
        />
      )}
      {sheet === 'convert' && (
        <ConvertMarketSheet
          open
          onOpenChange={(open) => !open && setSheet(null)}
          workspaceId={ws}
          marketRef={ref}
          geocodingEnabled={geocodingEnabled}
          onConverted={(converted) =>
            navigate(`/mais/feira/mercado/${converted.familyId ?? converted.ref}`, {
              replace: true,
            })
          }
        />
      )}
    </main>
  )
}
