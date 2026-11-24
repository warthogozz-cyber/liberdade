import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { legalConfig } from './legalConfig'
import { LegalLayout } from './LegalLayout'

const SECTIONS = [
  { key: 'what', title: 'bot.what.title', body: 'bot.what.p1' },
  { key: 'where', title: 'bot.where.title', body: 'bot.where.p1' },
  { key: 'when', title: 'bot.when.title', body: 'bot.when.p1' },
  { key: 'contact', title: 'bot.contact.title', body: 'bot.contact.p1' },
] as const

/**
 * What the indicator robot is, public so the address in its `User-Agent` leads somewhere: what it
 * collects, from where, when, and who to ask. The facts are those of the Banco Central job.
 */
export function BotPage() {
  const intl = useIntl()
  return (
    <LegalLayout
      title={intl.formatMessage({ id: 'bot.title' })}
      intro={intl.formatMessage({ id: 'bot.intro' })}
    >
      {SECTIONS.map((section) => (
        <section key={section.key} className="flex flex-col gap-2">
          <h2 className="mt-2 text-lg font-semibold">
            {intl.formatMessage({ id: section.title })}
          </h2>
          <p>
            {intl.formatMessage(
              { id: section.body },
              {
                email: legalConfig.contactEmail,
                policy: (
                  <Link
                    to="/privacidade"
                    className="font-medium text-primary underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    {intl.formatMessage({ id: 'bot.contact.policy' })}
                  </Link>
                ),
              },
            )}
          </p>
        </section>
      ))}
    </LegalLayout>
  )
}
