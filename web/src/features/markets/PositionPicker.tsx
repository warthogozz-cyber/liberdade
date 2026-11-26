import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { geocodeAddress, type GeocodedPlace, type MarketPoint } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { roundTo } from './geo'
import { readPosition, type PositionError } from './position'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-60 ${focus}`

/** A position the person marked, and the address it came from when it was looked up. */
export type PickedPosition = MarketPoint & { label?: string }

interface PositionPickerProps {
  value: PickedPosition | null
  onChange: (position: PickedPosition | null) => void
  workspaceId: string
  /** False when the address lookup is switched off: only "Estou no mercado agora" is left. */
  geocodingEnabled: boolean
}

/**
 * Where a market is: "Estou no mercado agora" reads the device only when tapped (to 5 decimals), or
 * "Buscar endereço" looks an address up and lists the places. Nothing here is saved on the device.
 */
export function PositionPicker({
  value,
  onChange,
  workspaceId,
  geocodingEnabled,
}: PositionPickerProps) {
  const intl = useIntl()
  const [failure, setFailure] = useState<PositionError | 'geocode' | null>(null)
  const [busy, setBusy] = useState(false)
  const [searching, setSearching] = useState(false)
  const [query, setQuery] = useState('')
  const [places, setPlaces] = useState<GeocodedPlace[] | null>(null)
  const lookup = useMutation({ mutationFn: (text: string) => geocodeAddress(workspaceId, text) })

  const here = async () => {
    setFailure(null)
    setBusy(true)
    let point
    try {
      point = await readPosition({ enableHighAccuracy: true })
    } catch (error) {
      setFailure(error instanceof Error && error.message === 'denied' ? 'denied' : 'unavailable')
      setBusy(false)
      return
    }
    setBusy(false)
    onChange({ lat: roundTo(point.lat, 5), lon: roundTo(point.lon, 5) })
  }

  const find = (event: FormEvent) => {
    event.preventDefault()
    const text = query.trim()
    if (text === '') return
    setFailure(null)
    lookup.mutate(text, {
      onSuccess: (found) => setPlaces(found.slice(0, 5)),
      onError: () => {
        setPlaces(null)
        setFailure('geocode')
      },
    })
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={() => void here()} disabled={busy} className={button}>
          <FormattedMessage id="markets.picker.here" />
        </button>
        {geocodingEnabled && (
          <button type="button" onClick={() => setSearching(true)} className={button}>
            <FormattedMessage id="markets.picker.search" />
          </button>
        )}
      </div>

      {geocodingEnabled && searching && (
        <div className="flex flex-col gap-3">
          <form onSubmit={find} className="flex items-end gap-2">
            <label className="flex flex-1 flex-col gap-1 text-sm">
              <FormattedMessage id="markets.picker.query" />
              <input
                type="text"
                value={query}
                maxLength={120}
                onChange={(event) => setQuery(event.target.value)}
                autoComplete="off"
                className="min-h-12 rounded-field border border-text-muted bg-background px-3 text-base"
              />
            </label>
            <button
              type="submit"
              disabled={lookup.isPending || query.trim() === ''}
              className={button}
            >
              <FormattedMessage
                id={lookup.isPending ? 'markets.picker.finding' : 'markets.picker.find'}
              />
            </button>
          </form>
          {places !== null &&
            (places.length === 0 ? (
              <p role="status">
                <FormattedMessage id="markets.picker.noPlaces" />
              </p>
            ) : (
              <div
                role="radiogroup"
                aria-label={intl.formatMessage({ id: 'markets.picker.places' })}
                className="flex flex-col gap-1"
              >
                {places.map((each) => (
                  <label
                    key={`${each.lat},${each.lon},${each.label}`}
                    className="flex min-h-12 items-center gap-3"
                  >
                    <input
                      type="radio"
                      name="market-place"
                      checked={value?.lat === each.lat && value.lon === each.lon}
                      onChange={() => onChange({ lat: each.lat, lon: each.lon, label: each.label })}
                      className="size-5 accent-primary"
                    />
                    {each.label}
                  </label>
                ))}
              </div>
            ))}
          <p className="text-sm text-text-muted">
            <FormattedMessage id="markets.picker.credit" />
          </p>
        </div>
      )}

      {busy && (
        <p role="status">
          <FormattedMessage id="markets.picker.locating" />
        </p>
      )}
      {failure !== null && (
        <p role="alert">
          <FormattedMessage
            id={
              failure === 'denied'
                ? 'markets.picker.denied'
                : failure === 'geocode'
                  ? 'markets.picker.geocodeFailed'
                  : 'markets.picker.unavailable'
            }
          />
        </p>
      )}
      {value !== null && (
        <p role="status" className="flex flex-wrap items-center gap-2 text-sm">
          {value.label !== undefined ? (
            <FormattedMessage id="markets.picker.placeSet" values={{ label: value.label }} />
          ) : (
            <FormattedMessage
              id="markets.picker.set"
              values={{ point: `${value.lat}, ${value.lon}` }}
            />
          )}
          <button type="button" onClick={() => onChange(null)} className={`${button} min-h-12`}>
            <FormattedMessage id="markets.picker.clear" />
          </button>
        </p>
      )}
    </div>
  )
}
