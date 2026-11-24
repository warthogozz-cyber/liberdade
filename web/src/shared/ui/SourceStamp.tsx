import { FormattedMessage, useIntl } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { sourceStampMessages } from './sourceStamp.messages.pt-BR'

// The text loads with the first lazy chunk that uses the stamp, not with the app.
registerMessages(sourceStampMessages)

interface SourceStampProps {
  source: string
  /** The source's page; it becomes a link only when it is an `https://` address. */
  url?: string | null
  /** When the source was last updated (ISO instant), or null if never. */
  updatedAt: string | null
  stale: boolean
}

/** "Fonte: Banco Central do Brasil · atualizado em 24/11/2026", with a text badge when stale. */
export function SourceStamp({ source, url, updatedAt, stale }: SourceStampProps) {
  const intl = useIntl()
  const name =
    url?.startsWith('https://') === true ? (
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        {source}
      </a>
    ) : (
      source
    )
  // The update date is the source's, so it ignores the family's time zone.
  const known = updatedAt !== null && !Number.isNaN(Date.parse(updatedAt))
  const date = !known
    ? ''
    : intl.formatDate(updatedAt, {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        timeZone: 'America/Sao_Paulo',
      })
  return (
    <p className="text-sm text-text-muted">
      <FormattedMessage
        id={!known ? 'sourceStamp.never' : 'sourceStamp.updated'}
        values={{ source: name, date }}
      />
      {stale ? (
        <span className="ml-2 inline-block rounded-full border border-text-muted px-2 py-0.5 text-xs font-medium text-text">
          <FormattedMessage id="sourceStamp.stale" />
        </span>
      ) : null}
    </p>
  )
}
