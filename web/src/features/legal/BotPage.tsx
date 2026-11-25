import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { legalConfig } from './legalConfig'
import { LegalLayout } from './LegalLayout'

const SECTIONS = [
  { key: 'what', title: 'bot.what.title', bodies: ['bot.what.p1'] },
  { key: 'where', title: 'bot.where.title', bodies: ['bot.where.p1'] },
  { key: 'when', title: 'bot.when.title', bodies: ['bot.when.p1'] },
  {
    key: 'markets',
    title: 'bot.markets.title',
    bodies: ['bot.markets.p1', 'bot.markets.p2', 'bot.markets.p3'],
  },
  { key: 'third', title: 'bot.third.title', bodies: ['bot.third.p1', 'bot.third.p2'] },
  { key: 'contact', title: 'bot.contact.title', bodies: ['bot.contact.p1'] },
] as const

const OSM_COPYRIGHT = 'https://www.openstreetmap.org/copyright'
const LINK_CLASS =
  'font-medium text-primary underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * What the indicator robot is, public so the address in its `User-Agent` leads somewhere: what it
 * collects, from where, when, and who to ask. The facts are those of the Banco Central job and of the Feira collections (Geofabrik, Nominatim, the Instagram oEmbed), with the OpenStreetMap attribution.
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
          {section.bodies.map((body) => (
            <p key={body}>
              {intl.formatMessage(
                { id: body },
                {
                  email: legalConfig.contactEmail,
                  policy: (
                    <Link to="/privacidade" className={LINK_CLASS}>
                      {intl.formatMessage({ id: 'bot.contact.policy' })}
                    </Link>
                  ),
                  link: (
                    <a href={OSM_COPYRIGHT} className={LINK_CLASS} target="_blank" rel="noreferrer">
                      {intl.formatMessage({ id: 'bot.third.link' })}
                    </a>
                  ),
                },
              )}
            </p>
          ))}
        </section>
      ))}
    </LegalLayout>
  )
}
