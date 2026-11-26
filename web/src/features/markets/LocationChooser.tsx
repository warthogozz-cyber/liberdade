import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { geocodeAddress, type GeocodedPlace } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { Sheet } from '@/shared/ui/Sheet'
import { roundTo, type GeoPoint } from './geo'
import {
  writeLocation,
  type LocationMode,
  type StoredLocation,
  type StoredPoint,
} from './locationStore'
import { marketsMessages } from './messages.pt-BR'
import { readPosition, type PositionError } from './position'

registerMessages(marketsMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`
const primary = `${button} bg-primary text-on-primary disabled:opacity-60`

interface LocationChooserProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  userId: string
  workspaceId: string
  /** The saved choice, which the dialog starts on. */
  current: StoredLocation | null
  /** False when the address lookup is switched off: the typed-address option goes away. */
  geocodingEnabled: boolean
  /** True only when the family has an address and the person may read it. */
  homeAvailable: boolean
  /** The choice that was saved, and the precise position for this screen's memory (only for `precise`). */
  onChosen: (location: StoredLocation, precise: GeoPoint | null) => void
}

/** The browser's own approximate-location button (Chrome 144+), which asks only when tapped. */
function ApproximateElement({
  onPoint,
  onFailure,
}: {
  onPoint: (point: GeoPoint) => void
  onFailure: (error: PositionError) => void
}) {
  const host = useRef<HTMLDivElement>(null)
  const callbacks = useRef({ onPoint, onFailure })
  useEffect(() => {
    callbacks.current = { onPoint, onFailure }
  })
  useEffect(() => {
    const element = document.createElement('geolocation')
    element.setAttribute('accuracymode', 'approximate')
    const onLocation = () => {
      const reading = element as unknown as {
        position?: { coords: GeolocationCoordinates }
        error?: { code: number }
      }
      if (reading.position) {
        callbacks.current.onPoint({
          lat: reading.position.coords.latitude,
          lon: reading.position.coords.longitude,
        })
      } else {
        callbacks.current.onFailure(reading.error?.code === 1 ? 'denied' : 'unavailable')
      }
    }
    element.addEventListener('location', onLocation)
    host.current?.append(element)
    return () => {
      element.removeEventListener('location', onLocation)
      element.remove()
    }
  }, [])
  return <div ref={host} />
}

const hasApproximateElement = () => 'HTMLGeolocationElement' in window

/** "Onde procurar mercados?": the person picks how, and the device asks for a position only on Continuar. */
export function LocationChooser({
  open,
  onOpenChange,
  userId,
  workspaceId,
  current,
  geocodingEnabled,
  homeAvailable,
  onChosen,
}: LocationChooserProps) {
  const intl = useIntl()
  const available = (candidate: LocationMode) =>
    candidate === 'typed' ? geocodingEnabled : candidate === 'home' ? homeAvailable : true
  const [mode, setMode] = useState<LocationMode | null>(
    current !== null && available(current.mode) ? current.mode : null,
  )
  const [failure, setFailure] = useState<PositionError | 'geocode' | null>(null)
  const [busy, setBusy] = useState(false)
  const [query, setQuery] = useState('')
  const [places, setPlaces] = useState<GeocodedPlace[] | null>(null)
  const [place, setPlace] = useState<GeocodedPlace | null>(null)
  const lookup = useMutation({ mutationFn: (text: string) => geocodeAddress(workspaceId, text) })

  const storedApproximate = current?.mode === 'approximate' ? (current.point ?? null) : null
  const storedTyped = current?.mode === 'typed' ? (current.point ?? null) : null

  const choose = (location: StoredLocation, precise: GeoPoint | null = null) => {
    const saved = writeLocation(userId, location)
    onChosen(saved, precise)
    onOpenChange(false)
  }

  const finishApproximate = (point: GeoPoint) =>
    choose({
      mode: 'approximate',
      point: { lat: roundTo(point.lat, 2), lon: roundTo(point.lon, 2) },
    })

  /** Runs a device reading; a refusal shows a message and leaves the dialog open. */
  const locate = async (options: PositionOptions, done: (point: GeoPoint) => void) => {
    setFailure(null)
    setBusy(true)
    let point: GeoPoint
    try {
      point = await readPosition(options)
    } catch (error) {
      setFailure(error instanceof Error && error.message === 'denied' ? 'denied' : 'unavailable')
      setBusy(false)
      return
    }
    setBusy(false)
    // Outside the try: a failure of the follow-up is not "location unavailable".
    done(point)
  }

  const refreshApproximate = () =>
    locate({ enableHighAccuracy: false, maximumAge: 600_000 }, finishApproximate)

  const typedPoint: StoredPoint | null =
    place !== null
      ? {
          lat: roundTo(Number(place.lat), 3),
          lon: roundTo(Number(place.lon), 3),
          label: place.label,
        }
      : storedTyped

  const onContinue = () => {
    if (mode === 'precise') {
      void locate({ enableHighAccuracy: true, maximumAge: 0 }, (point) =>
        choose({ mode: 'precise' }, point),
      )
    } else if (mode === 'approximate') {
      // The saved point is reused: asking again would show where the person really is.
      if (storedApproximate !== null) choose({ mode: 'approximate', point: storedApproximate })
      else void refreshApproximate()
    } else if (mode === 'typed' && typedPoint !== null) {
      choose({ mode: 'typed', point: typedPoint })
    } else if (mode === 'home') {
      choose({ mode: 'home' })
    }
  }

  const search = (event: FormEvent) => {
    event.preventDefault()
    const text = query.trim()
    if (text === '') return
    setFailure(null)
    setPlace(null)
    lookup.mutate(text, {
      onSuccess: (found) => setPlaces(found.slice(0, 5)),
      onError: () => {
        setPlaces(null)
        setFailure('geocode')
      },
    })
  }

  const options: { value: LocationMode; id: FormatjsIntl.Message['ids'] }[] = [
    { value: 'precise', id: 'markets.chooser.precise' },
    { value: 'approximate', id: 'markets.chooser.approximate' },
    ...(geocodingEnabled
      ? [{ value: 'typed' as const, id: 'markets.chooser.typed' as const }]
      : []),
    ...(homeAvailable ? [{ value: 'home' as const, id: 'markets.chooser.home' as const }] : []),
  ]
  const useElement = mode === 'approximate' && storedApproximate === null && hasApproximateElement()
  const canContinue = mode !== null && (mode !== 'typed' || typedPoint !== null) && !busy

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={<FormattedMessage id="markets.chooser.title" />}
    >
      <div className="flex flex-col gap-4">
        <div
          role="radiogroup"
          aria-label={intl.formatMessage({ id: 'markets.chooser.title' })}
          className="flex flex-col gap-2"
        >
          {options.map((option) => (
            <label key={option.value} className="flex min-h-12 items-center gap-3">
              <input
                type="radio"
                name="markets-mode"
                value={option.value}
                checked={mode === option.value}
                onChange={() => {
                  setMode(option.value)
                  setFailure(null)
                }}
                className="size-5 accent-primary"
              />
              <FormattedMessage id={option.id} />
            </label>
          ))}
        </div>

        {mode === 'approximate' && storedApproximate !== null && (
          <button
            type="button"
            onClick={() => void refreshApproximate()}
            disabled={busy}
            className={button}
          >
            <FormattedMessage id="markets.chooser.refresh" />
          </button>
        )}

        {mode === 'typed' && (
          <div className="flex flex-col gap-3">
            <form onSubmit={search} className="flex items-end gap-2">
              <label className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
                <FormattedMessage id="markets.chooser.address" />
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
                  id={lookup.isPending ? 'markets.chooser.searching' : 'markets.chooser.search'}
                />
              </button>
            </form>
            {places !== null &&
              (places.length === 0 ? (
                <p role="status">
                  <FormattedMessage id="markets.chooser.noPlaces" />
                </p>
              ) : (
                <div
                  role="radiogroup"
                  aria-label={intl.formatMessage({ id: 'markets.chooser.places' })}
                  className="flex flex-col gap-1"
                >
                  {places.map((each) => (
                    <label
                      key={`${each.lat},${each.lon},${each.label}`}
                      className="flex min-h-12 items-center gap-3"
                    >
                      <input
                        type="radio"
                        name="markets-place"
                        checked={place === each}
                        onChange={() => setPlace(each)}
                        className="size-5 accent-primary"
                      />
                      {each.label}
                    </label>
                  ))}
                </div>
              ))}
            <p className="text-sm text-text-muted">
              <FormattedMessage id="markets.chooser.geocodeCredit" />
            </p>
          </div>
        )}

        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.chooser.privacy" />
        </p>

        {busy && (
          <p role="status">
            <FormattedMessage id="markets.chooser.locating" />
          </p>
        )}
        {failure !== null && (
          <p role="alert">
            <FormattedMessage
              id={
                failure === 'denied'
                  ? 'markets.chooser.denied'
                  : failure === 'geocode'
                    ? 'markets.chooser.geocodeFailed'
                    : 'markets.chooser.unavailable'
              }
            />
          </p>
        )}

        {useElement ? (
          <ApproximateElement
            onPoint={finishApproximate}
            onFailure={(error) => setFailure(error)}
          />
        ) : (
          <button type="button" onClick={onContinue} disabled={!canContinue} className={primary}>
            <FormattedMessage id="markets.chooser.continue" />
          </button>
        )}
      </div>
    </Sheet>
  )
}
