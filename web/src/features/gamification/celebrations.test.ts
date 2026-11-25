import { describe, expect, it } from 'vitest'
import type { AchievementView } from '@/shared/api/gamification'
import { newlyEarned } from './celebrations'

const NOW = Date.parse('2026-11-24T15:00:00Z')
const minutesAgo = (m: number) => new Date(NOW - m * 60_000).toISOString()

const earned = (earnedAt: string | null, silent = false): AchievementView => ({
  key: 'FIRST_TRANSACTION',
  earnedAt,
  silent,
})

describe('newlyEarned', () => {
  it('nova e não silenciosa', () => {
    const before: AchievementView[] = [earned(null)]
    expect(newlyEarned(before, [earned(minutesAgo(0))], NOW)).toEqual(['FIRST_TRANSACTION'])
    expect(newlyEarned([], [earned(minutesAgo(1))], NOW)).toEqual(['FIRST_TRANSACTION'])
  })

  it('silenciosa não celebra', () => {
    expect(newlyEarned([], [earned(minutesAgo(0), true)], NOW)).toEqual([])
  })

  it('conquista antiga não celebra', () => {
    expect(newlyEarned([], [earned(minutesAgo(6))], NOW)).toEqual([])
  })

  it('já estava antes', () => {
    const list = [earned(minutesAgo(1))]
    expect(newlyEarned(list, list, NOW)).toEqual([])
  })
})
