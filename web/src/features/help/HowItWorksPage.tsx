import { useId, type ReactNode } from 'react'
import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace, useModules } from '@/shared/api'
import { glossary, moduleHelp, type ModuleHelp } from '@/shared/help'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { howItWorksMessages, type HowItWorksMessageId } from './messages.pt-BR'

registerMessages(howItWorksMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The catalog keeps the ids as text; they all live in this screen's bundle (checked in tests). */
const id = (messageId: string) => messageId as HowItWorksMessageId

const BASICS = ['help.start', 'help.roles', 'help.offline', 'help.live'] as const

function Section({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId()
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2">
      <h2 id={headingId} className="text-lg font-semibold">
        {title}
      </h2>
      {children}
    </section>
  )
}

function ModuleSection({ entry }: { entry: ModuleHelp }) {
  const intl = useIntl()
  return (
    <Section title={intl.formatMessage({ id: id(entry.titleId) })}>
      <p>{intl.formatMessage({ id: id(`help.module.${entry.module}`) })}</p>
      <Link
        to={entry.path}
        state={{ forceTour: entry.tour }}
        className={`flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`}
      >
        {intl.formatMessage({ id: 'help.tour.open' })}
      </Link>
    </Section>
  )
}

/** How the app works: the basics, a tour of each module the person can open, and the words. */
export function HowItWorksPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'help.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  const modules = useModules(workspace?.id).data
  // A Youth cannot open the projects, so their tour is not offered either.
  const youth = workspace?.role === 'YOUTH'
  const visible = moduleHelp.filter(
    (entry) =>
      modules?.find((module) => module.key === entry.module)?.enabled === true &&
      !(youth && entry.hiddenForYouth),
  )

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {BASICS.map((key) => (
        <Section key={key} title={intl.formatMessage({ id: `${key}.title` })}>
          <p>{intl.formatMessage({ id: key })}</p>
        </Section>
      ))}
      {visible.map((entry) => (
        <ModuleSection key={entry.module} entry={entry} />
      ))}
      <Section title={intl.formatMessage({ id: 'help.glossary' })}>
        <dl className="flex flex-col gap-3">
          {glossary.map(({ termId, bodyId }) => (
            <div key={termId}>
              <dt className="font-medium">{intl.formatMessage({ id: id(termId) })}</dt>
              <dd className="text-text-muted">{intl.formatMessage({ id: id(bodyId) })}</dd>
            </div>
          ))}
        </dl>
      </Section>
    </main>
  )
}
