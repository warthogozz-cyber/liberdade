import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace, useFormatMoney } from '@/shared/api'
import { useVentures, type VentureSummary } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import { useDocumentTitle } from '@/shared/ui'
import { venturesMessages } from './messages.pt-BR'

// The text loads with this lazy screen, not with the app.
registerMessages(venturesMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

/** The family's projects: the open ones first, the closed ones in a group that starts folded. */
export function VenturesListPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'ventures.title' }))
  const workspace = useCurrentWorkspace()
  const ventures = useVentures(workspace?.id ?? '')
  const [limitOpen, setLimitOpen] = useState(false)
  const canWrite = workspace?.role === 'OWNER' || workspace?.role === 'ADULT'
  const open = ventures.data?.ventures.filter((each) => each.status !== 'CLOSED') ?? []
  const closed = ventures.data?.ventures.filter((each) => each.status === 'CLOSED') ?? []
  const limitReached = ventures.data?.limitReached === true

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="ventures.title" />
      </h1>
      {ventures.data !== undefined ? (
        <>
          {ventures.data.ventures.length === 0 && (
            <p className="text-text-muted">
              <FormattedMessage id="ventures.empty" />
            </p>
          )}
          {open.length > 0 && (
            <ul className="flex flex-col gap-3">
              {open.map((venture) => (
                <VentureCard key={venture.id} venture={venture} />
              ))}
            </ul>
          )}
          {closed.length > 0 && (
            <details className="flex flex-col gap-3">
              <summary className={`min-h-12 cursor-pointer py-3 font-medium ${focus}`}>
                <FormattedMessage id="ventures.closed" values={{ count: closed.length }} />
              </summary>
              <ul className="mt-3 flex flex-col gap-3">
                {closed.map((venture) => (
                  <VentureCard key={venture.id} venture={venture} />
                ))}
              </ul>
            </details>
          )}
          {canWrite &&
            (limitReached ? (
              <>
                <button
                  type="button"
                  aria-expanded={limitOpen}
                  onClick={() => setLimitOpen((current) => !current)}
                  className={`${outline} self-start bg-primary text-on-primary`}
                >
                  <FormattedMessage id="ventures.start" />
                </button>
                {limitOpen && (
                  <p role="status">
                    <FormattedMessage
                      id="ventures.limitReached"
                      values={{
                        link: (chunks) => (
                          <Link
                            to="/mais/plano"
                            className={`font-medium text-primary underline ${focus}`}
                          >
                            {chunks}
                          </Link>
                        ),
                      }}
                    />
                  </p>
                )}
              </>
            ) : (
              <Link
                to="/mais/projetos/comecar"
                className={`${outline} self-start bg-primary text-on-primary`}
              >
                <FormattedMessage id="ventures.start" />
              </Link>
            ))}
        </>
      ) : ventures.isError ? (
        <div className="flex flex-col items-start gap-2">
          <p>
            <FormattedMessage id="common.error" />
          </p>
          <button type="button" onClick={() => void ventures.refetch()} className={outline}>
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : (
        <ul
          aria-busy="true"
          aria-label={intl.formatMessage({ id: 'ventures.loading' })}
          className="flex flex-col gap-3"
        >
          {[0, 1].map((slot) => (
            <li key={slot} className="h-28 rounded-card bg-surface" />
          ))}
        </ul>
      )}
    </main>
  )
}

function VentureCard({ venture }: { venture: VentureSummary }) {
  const intl = useIntl()
  const formatMoney = useFormatMoney()
  const payback =
    venture.paybackState === 'MONTHS' && venture.paybackMonths !== null
      ? intl.formatMessage({ id: 'ventures.payback.months' }, { months: venture.paybackMonths })
      : intl.formatMessage({ id: 'ventures.payback.never' })
  return (
    <li className="rounded-card bg-surface">
      <Link to={`/mais/projetos/${venture.id}`} className={`flex flex-col gap-2 p-4 ${focus}`}>
        <span className="flex items-baseline justify-between gap-2">
          <span className="font-medium">{venture.name}</span>
          <span className="text-sm text-text-muted">
            <FormattedMessage id={`ventures.status.${venture.status}`} />
          </span>
        </span>
        <dl className="flex flex-col gap-1 text-sm">
          <div className="flex justify-between gap-2">
            <dt className="text-text-muted">
              <FormattedMessage id="ventures.profit" />
            </dt>
            <dd>{formatMoney(parseCents(venture.monthlyProfit.amount))}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt className="text-text-muted">
              <FormattedMessage id="ventures.payback" />
            </dt>
            <dd>{payback}</dd>
          </div>
        </dl>
      </Link>
    </li>
  )
}
