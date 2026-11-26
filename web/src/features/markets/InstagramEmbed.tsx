import { useMutation } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { getInstagramEmbed } from '@/shared/api/markets'
import { isSafeLink } from './links'
import { ThirdPartyNotice } from './ThirdPartyNotice'
import { useConsent } from './useConsent'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

interface InstagramEmbedProps {
  userId: string | undefined
  workspaceId: string
  marketRef: string
  /** The market's Instagram address, kept as a plain link when the embed is refused. */
  url: string
}

/**
 * A post shown inside an isolated frame (no same origin), after a tap and the Meta notice. The
 * markup comes from the API and goes to the frame with `postMessage`: the app never inserts it.
 */
export function InstagramEmbed({ userId, workspaceId, marketRef, url }: InstagramEmbedProps) {
  const intl = useIntl()
  const consent = useConsent(userId, 'instagramConsent')
  const [asking, setAsking] = useState(false)
  const [html, setHtml] = useState<string | null>(null)
  const frame = useRef<HTMLIFrameElement>(null)
  const load = useMutation({
    mutationFn: () => getInstagramEmbed(workspaceId, marketRef),
    onSuccess: setHtml,
  })

  const start = () => {
    setAsking(false)
    load.mutate()
  }
  const hide = () => {
    setHtml(null)
    load.reset()
  }

  return (
    <div className="flex flex-col gap-2">
      {html === null ? (
        <button
          type="button"
          className={`${outline} self-start`}
          disabled={load.isPending}
          onClick={() => (consent.granted ? start() : setAsking(true))}
        >
          <FormattedMessage id="markets.instagram.show" />
        </button>
      ) : (
        <button type="button" className={`${outline} self-start`} onClick={hide}>
          <FormattedMessage id="markets.instagram.hide" />
        </button>
      )}
      {load.isPending && (
        <p role="status">
          <FormattedMessage id="markets.instagram.loading" />
        </p>
      )}
      {load.isError && (
        <div role="alert" className="flex flex-col gap-1">
          <p>
            <FormattedMessage id="markets.instagram.unavailable" />
          </p>
          {isSafeLink(url) && (
            <a
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              className={`font-medium text-primary underline ${focus}`}
            >
              <FormattedMessage id="markets.instagram.link" />
            </a>
          )}
        </div>
      )}
      {html !== null && (
        <iframe
          ref={frame}
          src="/embed/instagram"
          sandbox="allow-scripts allow-popups"
          title={intl.formatMessage({ id: 'markets.instagram.title' })}
          // The frame has an opaque origin (no `allow-same-origin`), so `'*'` is the only target.
          onLoad={() =>
            frame.current?.contentWindow?.postMessage({ type: 'liberdade-instagram', html }, '*')
          }
          className="min-h-[560px] w-full rounded-field border-0"
        />
      )}
      {asking && (
        <ThirdPartyNotice
          title={<FormattedMessage id="markets.instagram.noticeTitle" />}
          text={<FormattedMessage id="markets.instagram.notice" />}
          confirmLabel={<FormattedMessage id="markets.instagram.open" />}
          onCancel={() => setAsking(false)}
          onConfirm={(remember) => {
            if (remember) consent.remember()
            start()
          }}
        />
      )}
    </div>
  )
}
