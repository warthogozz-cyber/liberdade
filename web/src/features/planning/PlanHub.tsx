import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { PageTour } from '@/shared/help'
import { useDocumentTitle } from '@/shared/ui'
import './register'

const entryClass =
  'flex min-h-14 flex-col justify-center rounded-field border border-primary px-4 py-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

type Entry = { to: string; title: string; description: string; youth: boolean; tour: string }

const entries: Entry[] = [
  {
    to: '/planejar/orcamento',
    title: 'planning.budget.title',
    description: 'planning.budget.description',
    youth: false,
    tour: 'planning-budget',
  },
  {
    to: '/planejar/metas',
    title: 'planning.goals.title',
    description: 'planning.goals.description',
    youth: true,
    tour: 'planning-goals',
  },
  {
    to: '/planejar/reserva',
    title: 'planning.reserve.title',
    description: 'planning.reserve.description',
    youth: false,
    tour: 'planning-reserve',
  },
  {
    to: '/planejar/recorrencias',
    title: 'planning.recurrences.title',
    description: 'planning.recurrences.description',
    youth: true,
    tour: 'planning-recurring',
  },
]

/** The planning entry point: its screens, without the budget and the reserve for a Youth (US-10). */
export function PlanHub() {
  useDocumentTitle(useIntl().formatMessage({ id: 'planning.title' }))
  const role = useCurrentWorkspace()?.role
  const youth = role === 'YOUTH'
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="planning.title" />
      </h1>
      <ul className="flex flex-col gap-3">
        {(role === undefined ? [] : entries)
          .filter((entry) => !youth || entry.youth)
          .map((entry) => (
            <li key={entry.to}>
              <Link to={entry.to} data-tour={entry.tour} className={entryClass}>
                <span className="font-medium text-primary">
                  <FormattedMessage id={entry.title as 'planning.title'} />
                </span>
                <span className="text-sm text-text-muted">
                  <FormattedMessage id={entry.description as 'planning.title'} />
                </span>
              </Link>
            </li>
          ))}
      </ul>
      <PageTour id="planning" />
    </main>
  )
}
