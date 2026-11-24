import { useIntl } from 'react-intl'

/** "fim de nov": the month a projection point closes, from its last day (`YYYY-MM-DD`). */
export function useMonthEndLabel(): (monthEnd: string) => string {
  const intl = useIntl()
  return (monthEnd) => {
    const month = intl
      .formatDate(`${monthEnd}T00:00:00Z`, { month: 'short', timeZone: 'UTC' })
      .replace('.', '')
    return intl.formatMessage({ id: 'planning.projection.monthEnd' }, { month })
  }
}
