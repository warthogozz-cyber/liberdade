import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { ThirdPartyNotice } from './ThirdPartyNotice'
import { useConsent } from './useConsent'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

interface MapEmbedProps {
  userId: string | undefined
  name: string
  /** The market's stored position (never the person's). */
  lat: string
  lon: string
}

/** Google's map of the market. Nothing leaves for Google until the person taps and accepts the notice. */
export function MapEmbed({ userId, name, lat, lon }: MapEmbedProps) {
  const intl = useIntl()
  const consent = useConsent(userId, 'mapConsent')
  const [state, setState] = useState<'idle' | 'asking' | 'open'>('idle')
  // Read at render time, so a build without the key shows no button at all.
  const key = import.meta.env.VITE_GOOGLE_MAPS_EMBED_KEY
  if (key === undefined || key === '') return null

  const src = `https://www.google.com/maps/embed/v1/place?key=${encodeURIComponent(key)}&q=${lat},${lon}`

  return (
    <section className="flex flex-col gap-3">
      <button
        type="button"
        className={`${outline} self-start`}
        onClick={() => setState(state === 'open' ? 'idle' : consent.granted ? 'open' : 'asking')}
      >
        <FormattedMessage id={state === 'open' ? 'markets.map.hide' : 'markets.map.show'} />
      </button>
      {state === 'open' && (
        <iframe
          src={src}
          title={intl.formatMessage({ id: 'markets.map.title' }, { name })}
          referrerPolicy="strict-origin-when-cross-origin"
          loading="lazy"
          className="min-h-[200px] w-full rounded-field border-0"
        />
      )}
      {state === 'asking' && (
        <ThirdPartyNotice
          title={<FormattedMessage id="markets.map.noticeTitle" />}
          text={<FormattedMessage id="markets.map.notice" />}
          confirmLabel={<FormattedMessage id="markets.map.open" />}
          onCancel={() => setState('idle')}
          onConfirm={(remember) => {
            if (remember) consent.remember()
            setState('open')
          }}
        />
      )}
    </section>
  )
}
