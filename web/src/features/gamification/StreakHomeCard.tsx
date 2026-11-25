import { FormattedMessage } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { useStreak } from '@/shared/api/gamification'
import { registerMessages } from '@/shared/i18n'
import { gamificationMessages } from './messages.pt-BR'

registerMessages(gamificationMessages)

/** The weekly streak on Início, with a link to the achievements. */
export default function StreakHomeCard() {
  const streak = useStreak(useCurrentWorkspace()?.id ?? '')
  if (!streak.data) return null
  const { weeks, frozenWeeks } = streak.data

  return (
    <section className="flex flex-col gap-2 rounded-card bg-surface p-4">
      <h2 className="text-lg font-semibold">
        <FormattedMessage id="gamification.streak.title" />
      </h2>
      {weeks > 0 ? (
        <>
          <p className="font-medium">
            <FormattedMessage id="gamification.streak.weeks" values={{ weeks }} />
          </p>
          {frozenWeeks > 0 && (
            <p className="text-text-muted">
              <FormattedMessage id="gamification.streak.frozen" values={{ frozen: frozenWeeks }} />
            </p>
          )}
        </>
      ) : (
        <p className="text-text-muted">
          <FormattedMessage id="gamification.streak.none" />
        </p>
      )}
      <Link
        to="/mais/conquistas"
        className="flex min-h-12 items-center font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="gamification.open" />
      </Link>
    </section>
  )
}
