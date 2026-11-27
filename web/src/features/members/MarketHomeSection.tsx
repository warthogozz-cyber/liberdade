import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { ApiError, useCurrentWorkspace } from '@/shared/api'
import {
  deleteMarketHome,
  geocodeAddress,
  marketsKeys,
  putMarketHome,
  useMarketHome,
  useMarketsSettings,
  type GeocodedPlace,
} from '@/shared/api/markets'
import { useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { canSetMarketHome } from './permissions'
import './register'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-60 ${focus}`
const primary = `${button} bg-primary text-on-primary`
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'

/** The point the server keeps: 3 decimals at most, as text. */
const threeDecimals = (value: string) => Number(value).toFixed(3)

/**
 * The family's address for the Feira, set by an Owner or an Adult. What is typed only travels to
 * the address lookup, on the person's request: it is never stored, cached or sent with the save,
 * which carries just the chosen point and an optional label.
 */
export function MarketHomeSection() {
  const intl = useIntl()
  const toast = useToast()
  const queryClient = useQueryClient()
  const workspace = useCurrentWorkspace()
  const ws = workspace?.id
  const allowed = workspace !== undefined && canSetMarketHome(workspace.role)
  const home = useMarketHome(ws, allowed)
  const settings = useMarketsSettings(allowed ? ws : undefined)
  const [query, setQuery] = useState('')
  const [places, setPlaces] = useState<GeocodedPlace[] | null>(null)
  const [place, setPlace] = useState<GeocodedPlace | null>(null)
  const [label, setLabel] = useState('')
  const [confirmingRemoval, setConfirmingRemoval] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: marketsKeys.all(ws ?? '') })
  const lookup = useMutation({ mutationFn: (text: string) => geocodeAddress(ws as string, text) })
  const save = useMutation({
    mutationFn: (chosen: GeocodedPlace) =>
      putMarketHome(ws as string, {
        lat: threeDecimals(chosen.lat),
        lon: threeDecimals(chosen.lon),
        label: label.trim() === '' ? null : label.trim(),
      }),
    onSuccess: async () => {
      await refresh()
      setQuery('')
      setPlaces(null)
      setPlace(null)
      setLabel('')
      toast.show({ message: intl.formatMessage({ id: 'members.home.saved' }) })
    },
  })
  const remove = useMutation({
    mutationFn: () => deleteMarketHome(ws as string),
    onSuccess: async () => {
      await refresh()
      setConfirmingRemoval(false)
      toast.show({ message: intl.formatMessage({ id: 'members.home.removed' }) })
    },
  })

  if (ws === undefined || !allowed) return null

  const failed = (failure: unknown) =>
    setError(
      failure instanceof ApiError && failure.detail
        ? failure.detail
        : intl.formatMessage({ id: 'members.home.failed' }),
    )
  const geocodingEnabled = settings.data?.geocodingEnabled === true
  const current = home.data ?? null

  const search = (event: FormEvent) => {
    event.preventDefault()
    const text = query.trim()
    if (text === '') return
    setError(null)
    setPlace(null)
    lookup.mutate(text, {
      onSuccess: (found) => setPlaces(found.slice(0, 5)),
      onError: (failure) => {
        setPlaces(null)
        failed(failure)
      },
    })
  }

  const choose = (chosen: GeocodedPlace) => {
    setPlace(chosen)
    setLabel(chosen.label.slice(0, 40))
  }

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-medium">
        <FormattedMessage id="members.home.title" />
      </h2>
      <p className="text-sm text-text-muted">
        <FormattedMessage id="members.home.help" />
      </p>

      {home.isError ? (
        <p role="alert">
          <FormattedMessage id="members.home.loadFailed" />
        </p>
      ) : current !== null ? (
        <div className="flex items-center justify-between gap-2">
          <p>
            {current.label !== null && current.label !== '' ? (
              <FormattedMessage id="members.home.current" values={{ label: current.label }} />
            ) : (
              <FormattedMessage id="members.home.currentNoLabel" />
            )}
          </p>
          <button
            type="button"
            className={button}
            onClick={() => setConfirmingRemoval(true)}
            disabled={remove.isPending}
          >
            <FormattedMessage id="members.home.remove" />
          </button>
        </div>
      ) : (
        !home.isPending && (
          <p className="text-text-muted">
            <FormattedMessage id="members.home.none" />
          </p>
        )
      )}

      {geocodingEnabled ? (
        <div className="flex flex-col gap-3">
          <form onSubmit={search} className="flex items-end gap-2">
            <label className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
              <FormattedMessage id="members.home.address" />
              <input
                type="text"
                value={query}
                maxLength={120}
                onChange={(event) => setQuery(event.target.value)}
                autoComplete="off"
                className={field}
              />
            </label>
            <button
              type="submit"
              disabled={lookup.isPending || query.trim() === ''}
              className={button}
            >
              <FormattedMessage
                id={lookup.isPending ? 'members.home.searching' : 'members.home.search'}
              />
            </button>
          </form>
          {places !== null &&
            (places.length === 0 ? (
              <p role="status">
                <FormattedMessage id="members.home.noPlaces" />
              </p>
            ) : (
              <div
                role="radiogroup"
                aria-label={intl.formatMessage({ id: 'members.home.places' })}
                className="flex flex-col gap-1"
              >
                {places.map((each) => (
                  <label
                    key={`${each.lat},${each.lon},${each.label}`}
                    className="flex min-h-12 items-center gap-3"
                  >
                    <input
                      type="radio"
                      name="market-home-place"
                      checked={place === each}
                      onChange={() => choose(each)}
                      className="size-5 accent-primary"
                    />
                    {each.label}
                  </label>
                ))}
              </div>
            ))}
          {place !== null && (
            <div className="flex flex-col gap-3">
              <label className="flex flex-col gap-1 text-sm">
                <FormattedMessage id="members.home.label" />
                <input
                  type="text"
                  value={label}
                  maxLength={40}
                  onChange={(event) => setLabel(event.target.value)}
                  autoComplete="off"
                  className={field}
                />
              </label>
              <button
                type="button"
                className={primary}
                disabled={save.isPending}
                onClick={() => {
                  setError(null)
                  save.mutate(place, { onError: failed })
                }}
              >
                <FormattedMessage
                  id={save.isPending ? 'members.home.saving' : 'members.home.save'}
                />
              </button>
            </div>
          )}
          <p className="text-sm text-text-muted">
            <FormattedMessage id="members.home.credit" />
          </p>
        </div>
      ) : (
        current === null &&
        settings.isSuccess && (
          <p className="text-text-muted">
            <FormattedMessage id="members.home.off" />
          </p>
        )
      )}

      {error !== null && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}

      <ConfirmDialog
        open={confirmingRemoval}
        title={<FormattedMessage id="members.home.remove.title" />}
        body={<FormattedMessage id="members.home.remove.body" />}
        confirmLabel={<FormattedMessage id="members.home.remove" />}
        cancelLabel={<FormattedMessage id="members.cancel" />}
        confirmDisabled={remove.isPending}
        onConfirm={() => {
          setError(null)
          remove.mutate(undefined, {
            onError: (failure) => {
              setConfirmingRemoval(false)
              failed(failure)
            },
          })
        }}
        onCancel={() => setConfirmingRemoval(false)}
      />
    </section>
  )
}
