import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { Disclaimer } from '@/shared/ui/Disclaimer'
import { venturesMessages } from '../messages.pt-BR'
import { INVESTMENT_TOPICS, REVIEWED_ON } from './texts'

registerMessages(venturesMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

const FIELDS = ['whatItIs', 'risk', 'liquidity', 'guarantee', 'tax'] as const

/** "2026-11-25" as "25/11/2026". */
const dayFirst = (iso: string) => iso.split('-').reverse().join('/')

/** The five kinds of investment in plain words, in a fixed order. Educational, never a recommendation. */
export function InvestmentsPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'ventures.invest.title' }))
  return (
    <>
      <Disclaimer>{intl.formatMessage({ id: 'ventures.invest.disclaimer' })}</Disclaimer>
      <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
        <h1 className="text-2xl font-semibold">
          {intl.formatMessage({ id: 'ventures.invest.title' })}
        </h1>
        <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.invest.intro' })}</p>
        <Link to="/mais/projetos/investimentos/simulador" className={`${outline} self-start`}>
          {intl.formatMessage({ id: 'ventures.invest.simulate' })}
        </Link>
        {INVESTMENT_TOPICS.map((topic) => (
          <section
            key={topic.id}
            aria-labelledby={`topic-${topic.id}`}
            className="flex flex-col gap-3 rounded-card bg-surface p-4"
          >
            <h2 id={`topic-${topic.id}`} className="text-lg font-semibold">
              {topic.name}
            </h2>
            {FIELDS.map((field) => (
              <p key={field}>
                <span className="font-medium">
                  {intl.formatMessage({ id: `ventures.invest.${field}` })}
                </span>
                {': '}
                {topic[field]}
              </p>
            ))}
          </section>
        ))}
        <p className="text-sm text-text-muted">
          {intl.formatMessage({ id: 'ventures.invest.reviewed' }, { date: dayFirst(REVIEWED_ON) })}
        </p>
      </main>
    </>
  )
}
