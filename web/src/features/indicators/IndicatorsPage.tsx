import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { PageTour } from '@/shared/help'
import { useDocumentTitle } from '@/shared/ui'
import { Disclaimer } from '@/shared/ui/Disclaimer'
import { IndicatorCard } from './IndicatorCard'
import { indicatorsMessages } from './messages.pt-BR'
import { useIndicators } from './useIndicators'

// The text loads with this lazy screen, not with the app.
registerMessages(indicatorsMessages)

const outline =
  'flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The Banco Central indicators, with where each came from and when it was updated. */
export function IndicatorsPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'indicators.title' }))
  const indicators = useIndicators(useCurrentWorkspace()?.id)
  return (
    <>
      <Disclaimer>
        <FormattedMessage id="indicators.disclaimer" />
      </Disclaimer>
      <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
        <h1 className="text-2xl font-semibold">
          <FormattedMessage id="indicators.title" />
        </h1>
        {indicators.data !== undefined ? (
          <ul data-tour="indicators-cards" className="flex flex-col gap-3">
            {indicators.data.indicators.map((indicator) => (
              <IndicatorCard key={indicator.code} indicator={indicator} />
            ))}
          </ul>
        ) : indicators.isError ? (
          <div data-tour="indicators-cards" className="flex flex-col items-start gap-2">
            <p>
              <FormattedMessage id="common.error" />
            </p>
            <button type="button" onClick={() => void indicators.refetch()} className={outline}>
              <FormattedMessage id="common.retry" />
            </button>
          </div>
        ) : (
          <ul
            data-tour="indicators-cards"
            aria-busy="true"
            aria-label={intl.formatMessage({ id: 'indicators.loading' })}
            className="flex flex-col gap-3"
          >
            {[0, 1, 2, 3, 4].map((slot) => (
              <li key={slot} className="h-28 rounded-card bg-surface" />
            ))}
          </ul>
        )}
        <Link
          to="/mais/indicadores/calculadora"
          data-tour="indicators-calculator"
          className={outline}
        >
          <FormattedMessage id="indicators.calculator" />
        </Link>
        <Link
          to="/robo"
          className="min-h-12 self-start py-3 font-medium text-primary underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="indicators.howWeCollect" />
        </Link>
        <PageTour id="indicators" />
      </main>
    </>
  )
}
