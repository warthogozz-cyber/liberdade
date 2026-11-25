import { FormattedMessage, useIntl } from 'react-intl'
import { useCurrentWorkspace } from '@/shared/api'
import { useAchievements, useStreak } from '@/shared/api/gamification'
import { registerMessages } from '@/shared/i18n'
import { PageTour } from '@/shared/tips'
import { useDocumentTitle } from '@/shared/ui'
import { achievementHint, achievementName } from './achievementCopy'
import { gamificationMessages } from './messages.pt-BR'

// The text loads with this lazy screen, not with the app.
registerMessages(gamificationMessages)

const outline =
  'flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The streak and the achievements: the ones earned with their date, then how to get the rest. */
export function AchievementsPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'gamification.title' }))
  const ws = useCurrentWorkspace()?.id ?? ''
  const streak = useStreak(ws)
  const achievements = useAchievements(ws)

  const items = achievements.data?.items ?? []
  const earned = items.filter((item) => item.earnedAt !== null)
  const next = items.filter((item) => item.earnedAt === null)

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="gamification.title" />
      </h1>
      <section
        data-tour="achievements-streak"
        className="flex flex-col gap-1 rounded-card bg-surface p-4"
      >
        <h2 className="text-lg font-semibold">
          <FormattedMessage id="gamification.streak.title" />
        </h2>
        {streak.data &&
          (streak.data.weeks > 0 ? (
            <>
              <p className="font-medium">
                <FormattedMessage
                  id="gamification.streak.weeks"
                  values={{ weeks: streak.data.weeks }}
                />
              </p>
              {streak.data.frozenWeeks > 0 && (
                <p className="text-text-muted">
                  <FormattedMessage
                    id="gamification.streak.frozen"
                    values={{ frozen: streak.data.frozenWeeks }}
                  />
                </p>
              )}
            </>
          ) : (
            <p className="text-text-muted">
              <FormattedMessage id="gamification.streak.none" />
            </p>
          ))}
      </section>
      {achievements.data !== undefined ? (
        <div data-tour="achievements-list" className="flex flex-col gap-4">
          {earned.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-lg font-semibold">
                <FormattedMessage id="gamification.earned" />
              </h2>
              <ul className="flex flex-col gap-2">
                {earned.map((item) => (
                  <li key={item.key} className="flex flex-col rounded-card bg-surface p-4">
                    <span className="font-medium">{achievementName(intl, item.key)}</span>
                    <span className="text-text-muted">
                      <FormattedMessage
                        id="gamification.earnedOn"
                        values={{
                          date: intl.formatDate(item.earnedAt!, {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                            timeZone: 'America/Sao_Paulo',
                          }),
                        }}
                      />
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {next.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-lg font-semibold">
                <FormattedMessage id="gamification.next" />
              </h2>
              <ul className="flex flex-col gap-2">
                {next.map((item) => (
                  <li key={item.key} className="flex flex-col rounded-card bg-surface p-4">
                    <span className="font-medium">{achievementName(intl, item.key)}</span>
                    <span className="text-text-muted">{achievementHint(intl, item.key)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      ) : achievements.isError ? (
        <div className="flex flex-col items-start gap-2">
          <p>
            <FormattedMessage id="common.error" />
          </p>
          <button type="button" onClick={() => void achievements.refetch()} className={outline}>
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : (
        <ul
          aria-busy="true"
          aria-label={intl.formatMessage({ id: 'gamification.loading' })}
          className="flex flex-col gap-2"
        >
          {[0, 1, 2].map((slot) => (
            <li key={slot} className="h-16 rounded-card bg-surface" />
          ))}
        </ul>
      )}
      <PageTour id="achievements" />
    </main>
  )
}
