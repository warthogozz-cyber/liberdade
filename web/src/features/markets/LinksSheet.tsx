import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import type { MarketCard, MarketLinksBody } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { Sheet } from '@/shared/ui/Sheet'
import {
  isValidHttpsUrl,
  isValidInstagram,
  MAX_FLYER_SOURCE_LENGTH,
  MAX_LINK_LENGTH,
} from './links'
import { useSaveLinks } from './useMarketWrites'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

interface LinksSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  workspaceId: string
  /** The ref the card was opened with. */
  marketRef: string
  card: MarketCard
}

type Field = 'website' | 'instagram' | 'flyerUrl' | 'flyerSource'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const input = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'

/**
 * Edits a market's links. The card's own values start the fields only when they are the family's:
 * a value that shows from OpenStreetMap starts empty, so saving never copies it into the family's row.
 * All five fields always go out (an empty one is `null`, which clears the family's value).
 */
export function LinksSheet({ open, onOpenChange, workspaceId, marketRef, card }: LinksSheetProps) {
  const intl = useIntl()
  const save = useSaveLinks(workspaceId, marketRef)
  const [website, setWebsite] = useState(
    card.website?.source === 'FAMILY' ? card.website.value : '',
  )
  const [instagram, setInstagram] = useState(
    card.instagram?.source === 'FAMILY' ? card.instagram.value : '',
  )
  const [flyerUrl, setFlyerUrl] = useState(card.flyer?.url ?? '')
  const [flyerSource, setFlyerSource] = useState(card.flyer?.source ?? '')
  const [flyerValidUntil, setFlyerValidUntil] = useState(card.flyer?.validUntil ?? '')
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const typed = {
      website: website.trim(),
      instagram: instagram.trim(),
      flyerUrl: flyerUrl.trim(),
      flyerSource: flyerSource.trim(),
    }
    const found: Partial<Record<Field, string>> = {}
    const httpsOnly = intl.formatMessage({ id: 'markets.links.httpsOnly' })
    if (typed.website !== '' && !isValidHttpsUrl(typed.website)) found.website = httpsOnly
    if (typed.flyerUrl !== '' && !isValidHttpsUrl(typed.flyerUrl)) found.flyerUrl = httpsOnly
    if (typed.instagram !== '' && !isValidInstagram(typed.instagram)) {
      found.instagram = intl.formatMessage({ id: 'markets.links.instagramInvalid' })
    }
    if (
      typed.flyerUrl !== '' &&
      (typed.flyerSource === '' || typed.flyerSource.length > MAX_FLYER_SOURCE_LENGTH)
    ) {
      found.flyerSource = intl.formatMessage({ id: 'markets.links.flyerSourceRequired' })
    }
    setErrors(found)
    if (Object.keys(found).length > 0) return

    const hasFlyer = typed.flyerUrl !== ''
    const body: MarketLinksBody = {
      website: typed.website || null,
      instagram: typed.instagram || null,
      flyerUrl: typed.flyerUrl || null,
      flyerSource: hasFlyer ? typed.flyerSource : null,
      flyerValidUntil: hasFlyer && flyerValidUntil !== '' ? flyerValidUntil : null,
    }
    save.mutate(body, { onSuccess: () => onOpenChange(false) })
  }

  const apiError = !save.isError
    ? null
    : save.error instanceof ApiError && save.error.detail
      ? save.error.detail
      : intl.formatMessage({ id: 'markets.form.saveFailed' })

  const text = (
    id:
      | 'markets.links.website'
      | 'markets.links.instagram'
      | 'markets.links.flyerUrl'
      | 'markets.links.flyerSource',
    field: Field,
    value: string,
    set: (next: string) => void,
    maxLength: number,
    inputMode?: 'url',
    osmValue?: string,
  ) => (
    <label className="flex flex-col gap-1 text-sm">
      <FormattedMessage id={id} />
      <input
        type="text"
        value={value}
        maxLength={maxLength}
        inputMode={inputMode}
        onChange={(event) => set(event.target.value)}
        autoComplete="off"
        aria-invalid={errors[field] !== undefined}
        aria-describedby={errors[field] !== undefined ? `links-${field}-error` : undefined}
        className={input}
      />
      {osmValue !== undefined && value === '' && (
        <span className="text-text-muted">
          <FormattedMessage id="markets.card.osmValue" values={{ value: osmValue }} />
        </span>
      )}
      {errors[field] !== undefined && (
        <span id={`links-${field}-error`} role="alert">
          {errors[field]}
        </span>
      )}
    </label>
  )

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={<FormattedMessage id="markets.links.title" />}
    >
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        {text(
          'markets.links.website',
          'website',
          website,
          setWebsite,
          MAX_LINK_LENGTH,
          'url',
          card.website?.source === 'OSM' ? card.website.value : undefined,
        )}
        {text(
          'markets.links.instagram',
          'instagram',
          instagram,
          setInstagram,
          MAX_LINK_LENGTH,
          undefined,
          card.instagram?.source === 'OSM' ? card.instagram.value : undefined,
        )}
        {text('markets.links.flyerUrl', 'flyerUrl', flyerUrl, setFlyerUrl, MAX_LINK_LENGTH, 'url')}
        {text(
          'markets.links.flyerSource',
          'flyerSource',
          flyerSource,
          setFlyerSource,
          MAX_FLYER_SOURCE_LENGTH + 20,
        )}
        <label className="flex flex-col gap-1 text-sm">
          <FormattedMessage id="markets.links.flyerValidUntil" />
          <input
            type="date"
            value={flyerValidUntil}
            onChange={(event) => setFlyerValidUntil(event.target.value)}
            className={input}
          />
        </label>
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.links.hint" />
        </p>
        {apiError !== null && <p role="alert">{apiError}</p>}
        <button
          type="submit"
          disabled={save.isPending}
          className={`flex min-h-12 items-center justify-center rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-60 ${focus}`}
        >
          <FormattedMessage id={save.isPending ? 'markets.form.saving' : 'markets.form.save'} />
        </button>
      </form>
    </Sheet>
  )
}
