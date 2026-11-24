import { describe, expect, it } from 'vitest'
import { goalsToCelebrate } from './celebration'

const now = new Date('2026-11-23T12:00:00Z')
const day = 24 * 60 * 60 * 1000
const reached = (id: string, daysAgo: number | null) => ({
  id,
  achievedAt: daysAgo === null ? null : new Date(now.getTime() - daysAgo * day).toISOString(),
})

describe('goalsToCelebrate', () => {
  it('celebrates recent unseen', () => {
    expect(goalsToCelebrate([reached('a', 1)], new Set(), now)).toEqual({
      celebrate: ['a'],
      markSilently: [],
    })
  })

  it('doesNotCelebrateOldGoals', () => {
    expect(goalsToCelebrate([reached('a', 8)], new Set(), now)).toEqual({
      celebrate: [],
      markSilently: ['a'],
    })
  })

  it('already celebrated is skipped', () => {
    expect(goalsToCelebrate([reached('a', 1), reached('b', 9)], new Set(['a', 'b']), now)).toEqual({
      celebrate: [],
      markSilently: [],
    })
  })

  it('ignores goals not reached yet', () => {
    expect(goalsToCelebrate([reached('a', null)], new Set(), now)).toEqual({
      celebrate: [],
      markSilently: [],
    })
  })
})
