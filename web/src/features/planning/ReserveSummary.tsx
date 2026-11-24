import { useIntl } from 'react-intl'
import { useFormatMoney } from '@/shared/api'
import type { ReserveView } from '@/shared/api/planning'
import { parseCents } from '@/shared/money'
import './register'

/** How much of the target the reserve covers, in whole percent, from 0 to 100. */
function reservePercent(reserve: ReserveView): number {
  if (reserve.months === null || reserve.targetMonths <= 0) return 0
  return Math.min(100, Math.round((reserve.months / reserve.targetMonths) * 100))
}

/**
 * What the reserve covers: "A reserva cobre 4,0 meses de gastos", the bar to the target and what
 * is missing. Nothing here is a warning: the bar keeps one colour and the words say the rest.
 */
export function ReserveSummary({ reserve }: { reserve: ReserveView }) {
  const intl = useIntl()
  const format = useFormatMoney()
  const { months, missing, targetMonths } = reserve
  if (months === null) return null
  const percent = reservePercent(reserve)
  const met = missing !== null && parseCents(missing.amount) === 0n
  return (
    <div className="flex flex-col gap-2">
      <p className="text-lg font-medium">
        {intl.formatMessage(
          { id: 'planning.reserve.covers' },
          {
            months: intl.formatNumber(months, {
              minimumFractionDigits: 1,
              maximumFractionDigits: 1,
            }),
          },
        )}
      </p>
      <span
        role="meter"
        aria-label={intl.formatMessage({ id: 'planning.reserve.meter' })}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="block h-2 overflow-hidden rounded-full bg-text-muted/20"
      >
        <span className="block h-full rounded-full bg-primary" style={{ width: `${percent}%` }} />
      </span>
      {missing !== null && (
        <p className="text-text-muted">
          {met
            ? intl.formatMessage({ id: 'planning.reserve.met' }, { target: targetMonths })
            : intl.formatMessage(
                { id: 'planning.reserve.missing' },
                { amount: format(parseCents(missing.amount)), target: targetMonths },
              )}
        </p>
      )}
    </div>
  )
}
