import type { IntlShape } from 'react-intl'
import type { AchievementKey } from '@/shared/api/gamification'

/** The achievement's name, as the card, the screen and the celebration say it. */
export function achievementName(intl: IntlShape, key: AchievementKey): string {
  return intl.formatMessage({ id: `gamification.achievement.${key}.name` })
}

/** How to get the achievement, shown while it is not earned. */
export function achievementHint(intl: IntlShape, key: AchievementKey): string {
  return intl.formatMessage({ id: `gamification.achievement.${key}.hint` })
}
