import { FormattedMessage, useIntl } from 'react-intl'

interface DataAsOfProps {
  /** When the data on screen was loaded: a query's `dataUpdatedAt`. */
  updatedAt: number
  /** The family's time zone, so the hour matches the family's clock. */
  timeZone: string
}

/** "Mostrando dados de 15/11, 11:30.": when the cached data shown offline was loaded (FR-I3). */
export function DataAsOf({ updatedAt, timeZone }: DataAsOfProps) {
  const intl = useIntl()
  const when = intl.formatDate(updatedAt, {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    timeZone,
  })
  return <FormattedMessage id="offline.dataAsOf" values={{ when }} />
}
