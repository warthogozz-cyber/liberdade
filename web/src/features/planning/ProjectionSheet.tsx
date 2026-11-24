import { useIntl } from 'react-intl'
import { useFormatMoney } from '@/shared/api'
import type { ProjectionView } from '@/shared/api/planning'
import { negateCents, parseCents } from '@/shared/money'
import { Sheet } from '@/shared/ui/Sheet'
import { useMonthEndLabel } from './projectionLabel'
import './register'

interface ProjectionSheetProps {
  projection: ProjectionView
  onClose: () => void
}

/** How each projected balance is made: today's balance, what is scheduled and the estimated spending. */
export function ProjectionSheet({ projection, onClose }: ProjectionSheetProps) {
  const intl = useIntl()
  const format = useFormatMoney()
  const label = useMonthEndLabel()
  const today = format(parseCents(projection.balanceToday.amount))

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'planning.projection.sheetTitle' })}
    >
      <div className="flex flex-col gap-4">
        {projection.points.map((point) => (
          <section key={point.monthEnd} className="flex flex-col gap-1">
            <h3 className="font-semibold">{label(point.monthEnd)}</h3>
            <dl className="flex flex-col gap-1">
              <Line name={intl.formatMessage({ id: 'planning.projection.today' })} value={today} />
              <Line
                name={intl.formatMessage({ id: 'planning.projection.scheduled' })}
                value={format(parseCents(point.scheduled.amount))}
              />
              <Line
                name={intl.formatMessage({ id: 'planning.projection.estimated' })}
                value={format(negateCents(parseCents(point.estimatedSpending.amount)))}
              />
              <Line
                name={intl.formatMessage({ id: 'planning.projection.result' })}
                value={format(parseCents(point.balance.amount))}
                strong
              />
            </dl>
          </section>
        ))}
        <p className="text-text-muted">
          {intl.formatMessage({
            id: projection.hasHistory
              ? 'planning.projection.note'
              : 'planning.projection.noteNoHistory',
          })}
        </p>
      </div>
    </Sheet>
  )
}

function Line({ name, value, strong = false }: { name: string; value: string; strong?: boolean }) {
  return (
    <div
      className={`flex justify-between gap-4 ${strong ? 'border-t border-text-muted/20 pt-1 font-medium' : ''}`}
    >
      <dt>{name}</dt>
      <dd>{value}</dd>
    </div>
  )
}
