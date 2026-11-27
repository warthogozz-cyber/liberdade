import { useState } from 'react'
import { useIntl } from 'react-intl'
import { Link, useParams } from 'react-router'
import { ApiError, useCurrentWorkspace, useFormatMoney } from '@/shared/api'
import { useVenture } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import { useDocumentTitle } from '@/shared/ui'
import { FiguresPanel } from './FiguresPanel'
import { HowWeCalculate } from './HowWeCalculate'
import { venturesMessages } from './messages.pt-BR'
import { StatusSheet } from './StatusSheet'
import { StepsList } from './StepsList'

registerMessages(venturesMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

/** One project: its figures, how they are worked out, its steps and its status. */
export function VenturePage() {
  const intl = useIntl()
  const formatMoney = useFormatMoney()
  const { id = '' } = useParams()
  const workspace = useCurrentWorkspace()
  const venture = useVenture(workspace?.id ?? '', id)
  const [statusOpen, setStatusOpen] = useState(false)
  const canWrite = workspace?.role === 'OWNER' || workspace?.role === 'ADULT'
  useDocumentTitle(venture.data?.name ?? intl.formatMessage({ id: 'ventures.title' }))

  // Deleted elsewhere: a failed refetch keeps the cached data, so the 404 is checked first.
  if (venture.error instanceof ApiError && venture.error.status === 404) {
    return (
      <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
        <p>{intl.formatMessage({ id: 'ventures.page.gone' })}</p>
        <Link to="/mais/projetos" className={`${outline} self-start`}>
          {intl.formatMessage({ id: 'ventures.page.back' })}
        </Link>
      </main>
    )
  }

  if (workspace && venture.data) {
    const data = venture.data
    return (
      <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
        <header className="flex flex-col gap-2">
          <h1 className="text-2xl font-semibold">{data.name}</h1>
          <p className="text-text-muted">
            <span>{intl.formatMessage({ id: `ventures.type.${data.type}` })}</span>
            {' · '}
            <span>{intl.formatMessage({ id: `ventures.status.${data.status}` })}</span>
          </p>
          {canWrite && (
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={() => setStatusOpen(true)} className={outline}>
                {intl.formatMessage({ id: 'ventures.page.changeStatus' })}
              </button>
              <Link to={`/mais/projetos/${data.id}/editar`} className={outline}>
                {intl.formatMessage({ id: 'ventures.page.edit' })}
              </Link>
            </div>
          )}
        </header>
        <section
          aria-label={intl.formatMessage({ id: 'ventures.form.products' })}
          className="rounded-card bg-surface p-4"
        >
          <h2 className="mb-2 font-semibold">
            {intl.formatMessage({ id: 'ventures.form.products' })}
          </h2>
          <ul className="flex flex-col gap-2">
            {data.products.map((product) => (
              <li key={product.id} className="flex flex-col gap-1">
                <span className="font-medium">{product.name}</span>
                <dl className="flex flex-col gap-1 text-sm">
                  <div className="flex justify-between gap-3">
                    <dt className="text-text-muted">
                      {intl.formatMessage({ id: 'ventures.page.price' })}
                    </dt>
                    <dd className="tabular-nums">
                      {formatMoney(parseCents(product.unitPrice.amount))}
                    </dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-text-muted">
                      {intl.formatMessage({ id: 'ventures.page.cost' })}
                    </dt>
                    <dd className="tabular-nums">
                      {formatMoney(parseCents(product.unitVariableCost.amount))}
                    </dd>
                  </div>
                </dl>
              </li>
            ))}
          </ul>
        </section>
        <FiguresPanel
          figures={data.figures}
          currency={data.currency}
          productNames={data.products.map((product) => product.name)}
          live={false}
        />
        <HowWeCalculate venture={data} />
        {hasRun(data) && (
          <Link to={`/mais/projetos/${data.id}/real`} className={`${outline} self-start`}>
            {intl.formatMessage({ id: 'ventures.real.link' })}
          </Link>
        )}
        <StepsList workspace={workspace} venture={data} canWrite={canWrite} />
        {canWrite && (
          <StatusSheet
            workspace={workspace}
            venture={data}
            open={statusOpen}
            onOpenChange={setStatusOpen}
          />
        )}
      </main>
    )
  }

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      {venture.isError ? (
        <>
          <p>{intl.formatMessage({ id: 'common.error' })}</p>
          <button
            type="button"
            onClick={() => void venture.refetch()}
            className={`${outline} self-start`}
          >
            {intl.formatMessage({ id: 'common.retry' })}
          </button>
        </>
      ) : (
        <div aria-busy="true" className="h-40 rounded-card bg-surface" />
      )}
    </main>
  )
}

/**
 * Whether the project ever ran, so a real side can exist. Closing straight from planning writes the
 * close day as the start, the same rule the API's export uses; one run and closed that same day
 * looks the same and is left out too.
 */
function hasRun(venture: { status: string; startedOn?: string | null; closedOn?: string | null }) {
  if (venture.status === 'ACTIVE') return true
  if (venture.status !== 'CLOSED' || !venture.startedOn || !venture.closedOn) return false
  return venture.closedOn > venture.startedOn
}
