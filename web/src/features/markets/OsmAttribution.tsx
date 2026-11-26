import { FormattedMessage, useIntl } from 'react-intl'

/** The OpenStreetMap licence page. */
export const OSM_COPYRIGHT_URL = 'https://www.openstreetmap.org/copyright'

/** "Dados: © colaboradores do OpenStreetMap (ODbL) · 18/11": always on screen where OSM data shows. */
export function OsmAttribution({ date }: { date: string | null }) {
  const intl = useIntl()
  // The date is the data's own (a calendar day), so it ignores the family's time zone.
  const when =
    date === null || Number.isNaN(Date.parse(date))
      ? null
      : intl.formatDate(`${date}T00:00:00Z`, {
          day: '2-digit',
          month: '2-digit',
          timeZone: 'UTC',
        })
  return (
    <p className="text-sm text-text-muted">
      <FormattedMessage
        id="markets.attribution"
        values={{
          link: (chunks) => (
            <a
              href={OSM_COPYRIGHT_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              {chunks}
            </a>
          ),
        }}
      />
      {when !== null && <FormattedMessage id="markets.attribution.date" values={{ date: when }} />}
    </p>
  )
}
