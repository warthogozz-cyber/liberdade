/**
 * What the streak card and the achievements screen read. The `shared/api` barrel does not re-export
 * this file, so none of it weighs on the initial load. Both calls are reads: the API already keeps
 * out what the person's role or the family's modules do not show.
 */
import { useQuery } from '@tanstack/react-query'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type { components } from './schema'

type Schemas = components['schemas']

export type StreakView = Schemas['StreakView']
export type AchievementsView = Schemas['AchievementsView']
export type AchievementView = Schemas['AchievementView']
export type AchievementKey = Schemas['AchievementKey']

/** Everything the gamification module reads sits under one key, so one invalidation reaches it. */
export const gamificationKeys = {
  all: (ws: string) => [...queryKeys.workspace(ws), 'gamification'] as const,
  streak: (ws: string) => [...gamificationKeys.all(ws), 'streak'] as const,
  achievements: (ws: string) => [...gamificationKeys.all(ws), 'achievements'] as const,
}

/** The signed-in person's weekly streak. */
export function getStreak(ws: string): Promise<StreakView> {
  return unwrap(client.GET('/v1/workspaces/{ws}/gamification/streak', { params: { path: { ws } } }))
}

/** The person's achievements, in the order the screen shows them. */
export function getAchievements(ws: string): Promise<AchievementsView> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/gamification/achievements', { params: { path: { ws } } }),
  )
}

export function useStreak(ws: string) {
  return useQuery({
    queryKey: gamificationKeys.streak(ws),
    queryFn: () => getStreak(ws),
    enabled: ws !== '',
  })
}

export function useAchievements(ws: string) {
  return useQuery({
    queryKey: gamificationKeys.achievements(ws),
    queryFn: () => getAchievements(ws),
    enabled: ws !== '',
  })
}
