import { useState } from 'react'
import { useIntl } from 'react-intl'
import { useCurrentWorkspace, useFormatMoney } from '@/shared/api'
import { useProjection } from '@/shared/api/planning'
import { parseCents } from '@/shared/money'
import { ProjectionSheet } from './ProjectionSheet'
import { useMonthEndLabel } from './projectionLabel'
import './register'

/** The projected balance on Início: four estimates, each at the end of a month; a button explains them. */
export default function ProjectionHomeCard() {
  const intl = useIntl()
  const format = useFormatMoney()
  const label = useMonthEndLabel()
  const workspace = useCurrentWorkspace()
  const projection = useProjection(workspace?.id ?? '')
  const [open, setOpen] = useState(false)
  if (!projection.data || projection.data.points.length === 0) return null

  return (
    <>
      <section className="flex flex-col gap-3 rounded-card bg-surface p-4">
        <h2 className="text-lg font-semibold">
          {intl.formatMessage({ id: 'planning.projection.title' })}
        </h2>
        <ul className="grid grid-cols-2 gap-3">
          {projection.data.points.map((point) => (
            <li key={point.monthEnd} className="flex flex-col">
              {/* A negative balance keeps the text colour: it is an estimate, not an alarm. */}
              <span className="text-lg font-medium text-text">
                {format(parseCents(point.balance.amount))}
              </span>
              <span className="text-text-muted">{label(point.monthEnd)}</span>
            </li>
          ))}
        </ul>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="self-start rounded-card px-1 text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {intl.formatMessage({ id: 'planning.projection.sheetTitle' })}
        </button>
      </section>
      {open && <ProjectionSheet projection={projection.data} onClose={() => setOpen(false)} />}
    </>
  )
}
