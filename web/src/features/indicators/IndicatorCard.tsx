import { FormattedMessage, useIntl } from 'react-intl'
import type { components } from '@/shared/api/schema'
import { SourceStamp } from '@/shared/ui/SourceStamp'
import { formatIndicatorValue, formatVariation, isMonthly } from './format'

type IndicatorView = components['schemas']['IndicatorView']

/** One indicator: the value, how it moved, the day it refers to and where it came from. */
export function IndicatorCard({ indicator }: { indicator: IndicatorView }) {
  const intl = useIntl()
  const value = formatIndicatorValue(indicator)
  // The reference is a calendar day of the source, so it ignores the family's time zone.
  const referenceDate =
    indicator.referenceDate === null
      ? null
      : intl.formatDate(`${indicator.referenceDate}T00:00:00Z`, {
          timeZone: 'UTC',
          ...(isMonthly(indicator.code)
            ? { month: 'long', year: 'numeric' }
            : { day: '2-digit', month: '2-digit', year: 'numeric' }),
        })
  return (
    <li className="flex flex-col gap-1 rounded-card bg-surface p-4">
      <h2 className="font-medium">{indicator.name}</h2>
      {value === null ? (
        <p className="text-text-muted">
          <FormattedMessage id="indicators.noData" />
        </p>
      ) : (
        <>
          <p className="text-2xl font-semibold tabular-nums">{value}</p>
          {indicator.variation !== null && (
            <p className="text-sm text-text-muted">{formatVariation(indicator.variation)}</p>
          )}
          {referenceDate !== null && (
            <p className="text-sm text-text-muted">
              <FormattedMessage id="indicators.dataOf" values={{ date: referenceDate }} />
            </p>
          )}
        </>
      )}
      <SourceStamp
        source={indicator.source.name}
        url={indicator.source.url}
        updatedAt={indicator.updatedAt}
        stale={indicator.stale}
      />
    </li>
  )
}
