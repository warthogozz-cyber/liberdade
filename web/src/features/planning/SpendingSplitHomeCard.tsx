import { useIntl } from 'react-intl'
import { useCurrentWorkspace, useFormatMoney } from '@/shared/api'
import { useSpendingSplit } from '@/shared/api/planning'
import { currentMonth } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import './register'

/** The month's confirmed spending split into fixed and variable, with one divided bar. */
export default function SpendingSplitHomeCard() {
  const intl = useIntl()
  const format = useFormatMoney()
  const workspace = useCurrentWorkspace()
  const month = workspace ? currentMonth(new Date(), workspace.timezone) : ''
  const split = useSpendingSplit(workspace?.id ?? '', month)
  if (!split.data) return null

  const fixed = parseCents(split.data.fixed.amount)
  const variable = parseCents(split.data.variable.amount)
  const total = fixed + variable
  const percent = total > 0n ? Number((fixed * 100n) / total) : 0

  return (
    <section className="flex flex-col gap-3 rounded-card bg-surface p-4">
      <h2 className="text-lg font-semibold">
        {intl.formatMessage({ id: 'planning.split.title' })}
      </h2>
      <div className="flex flex-wrap justify-between gap-x-4">
        <p className="flex flex-col">
          <span className="text-text-muted">
            {intl.formatMessage({ id: 'planning.split.fixed' })}
          </span>
          <span className="text-lg font-medium">{format(fixed)}</span>
        </p>
        <p className="flex flex-col">
          <span className="text-text-muted">
            {intl.formatMessage({ id: 'planning.split.variable' })}
          </span>
          <span className="text-lg font-medium">{format(variable)}</span>
        </p>
      </div>
      {total > 0n && (
        <span
          role="meter"
          aria-label={intl.formatMessage({ id: 'planning.split.meter' })}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="block h-2 overflow-hidden rounded-full bg-text-muted/30"
        >
          <span className="block h-full bg-primary" style={{ width: `${percent}%` }} />
        </span>
      )}
    </section>
  )
}
