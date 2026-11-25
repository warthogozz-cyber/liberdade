import { useIntl } from 'react-intl'
import { useFormatMoney } from '@/shared/api'
import type { ActualVsPlanned } from '@/shared/api/ventures'
import { parseCents } from '@/shared/money'

interface Props {
  planned: ActualVsPlanned['planned']
  real: ActualVsPlanned['real']
}

/** The planned investment and payback beside the real ones, in the words of each state. */
export function RealPaybackCards({ planned, real }: Props) {
  const intl = useIntl()
  const formatMoney = useFormatMoney()
  const money = (amount: { amount: string }) => formatMoney(parseCents(amount.amount))
  const none = intl.formatMessage({ id: 'ventures.real.none' })

  const plannedPayback =
    planned.paybackState === 'NEVER' || planned.paybackMonths === null
      ? intl.formatMessage({ id: 'ventures.payback.never' })
      : intl.formatMessage({ id: 'ventures.payback.months' }, { months: planned.paybackMonths })

  const realPayback = () => {
    switch (real.state) {
      case 'PAID_BACK':
        return intl.formatMessage({ id: 'ventures.real.paidBack' }, { months: real.months ?? 0 })
      case 'NOT_YET':
        return intl.formatMessage(
          { id: 'ventures.real.notYet' },
          { missing: real.missing ? money(real.missing) : none },
        )
      case 'NEVER_NEGATIVE':
        return intl.formatMessage({ id: 'ventures.real.neverNegative' })
      case 'NO_DATA':
        return intl.formatMessage({ id: 'ventures.real.noData' })
    }
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <section
        aria-label={intl.formatMessage({ id: 'ventures.real.plannedCard' })}
        className="rounded-card bg-surface p-4"
      >
        <h2 className="mb-2 font-semibold">
          {intl.formatMessage({ id: 'ventures.real.plannedCard' })}
        </h2>
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex flex-col">
            <dt className="text-text-muted">
              {intl.formatMessage({ id: 'ventures.real.investment' })}
            </dt>
            <dd className="font-medium tabular-nums">{money(planned.initialInvestment)}</dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-text-muted">
              {intl.formatMessage({ id: 'ventures.real.payback' })}
            </dt>
            <dd className="font-medium">{plannedPayback}</dd>
          </div>
        </dl>
      </section>
      <section
        aria-label={intl.formatMessage({ id: 'ventures.real.actualCard' })}
        className="rounded-card bg-surface p-4"
      >
        <h2 className="mb-2 font-semibold">
          {intl.formatMessage({ id: 'ventures.real.actualCard' })}
        </h2>
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex flex-col">
            <dt className="text-text-muted">
              {intl.formatMessage({ id: 'ventures.real.investment' })}
            </dt>
            <dd className="font-medium tabular-nums">
              {real.investment ? money(real.investment) : none}
            </dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-text-muted">
              {intl.formatMessage({ id: 'ventures.real.payback' })}
            </dt>
            <dd className="font-medium">{realPayback()}</dd>
            {real.state === 'NEVER_NEGATIVE' && (
              <dd className="text-text-muted">
                {intl.formatMessage({ id: 'ventures.real.neverNegativeTip' })}
              </dd>
            )}
          </div>
        </dl>
      </section>
    </div>
  )
}
