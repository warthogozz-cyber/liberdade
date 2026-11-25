import type { AchievementKey, AchievementView } from '@/shared/api/gamification'

/** An achievement earned longer ago than this is listed without a celebration. */
const RECENT_MS = 5 * 60 * 1000

/**
 * Which achievements to celebrate: earned now, not in `before`, not silent (the API marks what came
 * in outside the person's own action) and earned in the last 5 minutes, so a list that was only
 * late to load never replays old news.
 */
export function newlyEarned(
  before: readonly AchievementView[],
  after: readonly AchievementView[],
  now: number,
): AchievementKey[] {
  const already = new Set(before.filter((item) => item.earnedAt !== null).map((item) => item.key))
  return after
    .filter((item) => {
      if (item.earnedAt === null || item.silent || already.has(item.key)) return false
      const age = now - new Date(item.earnedAt).getTime()
      return age <= RECENT_MS
    })
    .map((item) => item.key)
}
