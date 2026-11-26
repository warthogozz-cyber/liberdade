import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useMe } from '@/shared/api'
import {
  useMarketHome,
  useMarketsSettings,
  type MarketKind,
  type ReceiptLookup,
} from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { distanceMeters, type GeoPoint } from '../geo'
import { LocationChooser } from '../LocationChooser'
import { readLocation, type StoredLocation } from '../locationStore'
import { marketsMessages } from '../messages.pt-BR'
import { useKindLabel } from '../useKindLabel'
import { useMarketSearch } from '../useMarkets'
import { MAX_PRODUCT_NAME } from './productUnits'

registerMessages(marketsMessages)

/** What the person settled on: a market the app knows, or a name to make one from. */
export type MarketChoice =
  | { market: { ref: string }; newMarket?: undefined }
  | { market?: undefined; newMarket: { name: string; kind: Exclude<MarketKind, null> } }

/** The most nearby markets offered. */
const MAX_NEARBY = 8
const KINDS: Exclude<MarketKind, null>[] = [
  'SUPERMARKET',
  'CONVENIENCE',
  'GREENGROCER',
  'MARKETPLACE',
]
const OTHER = 'other'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`
const primary = `flex min-h-12 items-center justify-center rounded-field border border-primary bg-primary px-4 font-medium text-on-primary disabled:opacity-60 ${focus}`
const radioRow = 'flex min-h-12 items-center gap-3 rounded-card bg-surface px-4 py-2'

const toPoint = (lat: string, lon: string): GeoPoint => ({ lat: Number(lat), lon: Number(lon) })

interface ReceiptMarketStepProps {
  ws: string
  /** The market the key's CNPJ already belongs to; it cannot be changed. */
  known: ReceiptLookup['market']
  onChosen: (choice: MarketChoice) => void
}

/** The second step: the key's own market, or one picked from nearby, or a new one by name. */
export function ReceiptMarketStep({ ws, known, onChosen }: ReceiptMarketStepProps) {
  const kindLabel = useKindLabel()
  const intl = useIntl()
  if (known !== null) {
    const name =
      known.name ?? intl.formatMessage({ id: 'markets.unnamed' }, { kind: kindLabel(known.kind) })
    return (
      <div className="flex flex-col gap-4">
        <p>
          <FormattedMessage id="markets.receipt.market.known" />
        </p>
        <p className="text-lg font-semibold">{name}</p>
        <button
          type="button"
          onClick={() => onChosen({ market: { ref: known.ref } })}
          className={`${primary} self-start`}
        >
          <FormattedMessage id="markets.receipt.market.next" />
        </button>
      </div>
    )
  }
  return <ChooseMarket ws={ws} onChosen={onChosen} />
}

function ChooseMarket({ ws, onChosen }: { ws: string; onChosen: (choice: MarketChoice) => void }) {
  const intl = useIntl()
  const kindLabel = useKindLabel()
  const userId = useMe().data?.user.id
  const settings = useMarketsSettings(ws)
  const home = useMarketHome(ws, true)
  const [chosen, setChosen] = useState<StoredLocation | null>(null)
  // The precise position lives in this screen's memory only, as in the Feira list.
  const [precise, setPrecise] = useState<GeoPoint | null>(null)
  const [choosing, setChoosing] = useState(false)
  const [selected, setSelected] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [kind, setKind] = useState<Exclude<MarketKind, null>>('SUPERMARKET')

  const location = chosen ?? (userId !== undefined ? readLocation(userId) : null)
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
  const nearby =
    search.data && from
      ? [
          ...search.data.osm.map((each) => ({ ...each, name: each.name as string | null })),
          ...search.data.family,
        ]
          .map((each) => ({
            ref: each.ref,
            name: each.name,
            kind: each.kind,
            meters: distanceMeters(from, toPoint(each.lat, each.lon)),
          }))
          .sort((a, b) => a.meters - b.meters)
          .slice(0, MAX_NEARBY)
      : []

  const typedName = name.trim()
  const ready = selected !== null && (selected !== OTHER || typedName !== '')
  const next = () => {
    if (selected === null) return
    onChosen(
      selected === OTHER ? { newMarket: { name: typedName, kind } } : { market: { ref: selected } },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 font-medium">
          <FormattedMessage id="markets.receipt.market.choose" />
        </legend>
        {from === null && (
          <div className="flex flex-col items-start gap-2">
            <p className="text-text-muted">
              <FormattedMessage id="markets.receipt.market.needLocation" />
            </p>
            <button type="button" onClick={() => setChoosing(true)} className={outline}>
              <FormattedMessage id="markets.receipt.market.chooseLocation" />
            </button>
          </div>
        )}
        {nearby.map((each) => (
          <label key={each.ref} className={radioRow}>
            <input
              type="radio"
              name="market"
              value={each.ref}
              checked={selected === each.ref}
              onChange={() => setSelected(each.ref)}
              className="size-5"
            />
            <span className="flex flex-col">
              <span className="font-medium">
                {each.name ??
                  intl.formatMessage({ id: 'markets.unnamed' }, { kind: kindLabel(each.kind) })}
              </span>
              <span className="text-sm text-text-muted">{kindLabel(each.kind)}</span>
            </span>
          </label>
        ))}
        <label className={radioRow}>
          <input
            type="radio"
            name="market"
            value={OTHER}
            checked={selected === OTHER}
            onChange={() => setSelected(OTHER)}
            className="size-5"
          />
          <span className="font-medium">
            <FormattedMessage id="markets.receipt.market.other" />
          </span>
        </label>
      </fieldset>

      {selected === OTHER && (
        <div className="flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm font-medium">
            <FormattedMessage id="markets.receipt.market.otherName" />
            <input
              type="text"
              value={name}
              maxLength={MAX_PRODUCT_NAME}
              autoComplete="off"
              onChange={(event) => setName(event.target.value)}
              className={`${field} font-normal`}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm font-medium">
            <FormattedMessage id="markets.receipt.market.otherKind" />
            <select
              value={kind}
              onChange={(event) => setKind(event.target.value as Exclude<MarketKind, null>)}
              className={`${field} font-normal`}
            >
              {KINDS.map((each) => (
                <option key={each} value={each}>
                  {kindLabel(each)}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      <button type="button" disabled={!ready} onClick={next} className={`${primary} self-start`}>
        <FormattedMessage id="markets.receipt.market.next" />
      </button>

      {choosing && userId !== undefined && (
        <LocationChooser
          open
          onOpenChange={(open) => !open && setChoosing(false)}
          userId={userId}
          workspaceId={ws}
          current={location}
          geocodingEnabled={settings.data?.geocodingEnabled === true}
          homeAvailable={homePoint !== null}
          onChosen={(next, point) => {
            setChosen(next)
            setPrecise(point)
            setChoosing(false)
          }}
        />
      )}
    </div>
  )
}
