import { useIntl } from 'react-intl'
import { Link, useParams } from 'react-router'
import { ApiError, useCurrentWorkspace } from '@/shared/api'
import { useActualVsPlanned, useVenture } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { venturesMessages } from './messages.pt-BR'
import { MonthsTable } from './MonthsTable'
import { RealPaybackCards } from './RealPaybackCards'

registerMessages(venturesMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404

/** What a project really did against what it planned: the paybacks, then the months. */
export function ActualVsPlannedPage() {
  const intl = useIntl()
  const { id = '' } = useParams()
  const workspace = useCurrentWorkspace()
  const venture = useVenture(workspace?.id ?? '', id)
  const view = useActualVsPlanned(workspace?.id ?? '', id)
  useDocumentTitle(intl.formatMessage({ id: 'ventures.real.title' }))

  if (isNotFound(venture.error) || isNotFound(view.error)) {
    return (
      <main className="mx-auto flex max-w-2xl flex-col gap-4 p-6">
        <p>{intl.formatMessage({ id: 'ventures.page.gone' })}</p>
        <Link to="/mais/projetos" className={`${outline} self-start`}>
          {intl.formatMessage({ id: 'ventures.page.back' })}
        </Link>
      </main>
    )
  }

  if (venture.data && view.data) {
    return (
      <main className="mx-auto flex max-w-2xl flex-col gap-4 p-6">
        <header className="flex flex-col gap-2">
          <Link to={`/mais/projetos/${id}`} className="text-primary">
            {venture.data.name}
          </Link>
          <h1 className="text-2xl font-semibold">
            {intl.formatMessage({ id: 'ventures.real.title' })}
          </h1>
          <p className="text-text-muted">
            {intl.formatMessage(
              { id: 'ventures.real.status' },
              { status: intl.formatMessage({ id: `ventures.status.${venture.data.status}` }) },
            )}
          </p>
        </header>
        <RealPaybackCards planned={view.data.planned} real={view.data.real} />
        <MonthsTable months={view.data.months} totals={view.data.totals} />
        <Link to={`/lancamentos?projeto=${id}`} className={`${outline} self-start`}>
          {intl.formatMessage({ id: 'ventures.real.transactions' })}
        </Link>
      </main>
    )
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-4 p-6">
      {venture.isError || view.isError ? (
        <>
          <p>{intl.formatMessage({ id: 'common.error' })}</p>
          <button
            type="button"
            onClick={() => {
              void venture.refetch()
              void view.refetch()
            }}
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
