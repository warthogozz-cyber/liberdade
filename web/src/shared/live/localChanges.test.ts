import { afterEach, describe, expect, it } from 'vitest'
import {
  isLocalChange,
  isRecentArrival,
  markLocalChange,
  markRemoteArrival,
  onArrival,
  resetLocalChanges,
} from './localChanges'

const T = 1_000_000

afterEach(resetLocalChanges)

describe('localChanges', () => {
  it('localExpiresAfterFiveMinutes', () => {
    markLocalChange('a', T)
    expect(isLocalChange('a', T + 4 * 60_000 + 59_000)).toBe(true)
    expect(isLocalChange('a', T + 5 * 60_000)).toBe(false)
  })

  it('arrivalExpiresAfterSixtySeconds', () => {
    markRemoteArrival('a', T)
    expect(isRecentArrival('a', T + 59_000)).toBe(true)
    expect(isRecentArrival('a', T + 60_000)).toBe(false)
  })

  it('unknownIdsAreNeither', () => {
    expect(isLocalChange('x', T)).toBe(false)
    expect(isRecentArrival('x', T)).toBe(false)
  })

  it('arrivalsNotifyListenersUntilTheyUnsubscribe', () => {
    const seen: string[] = []
    const off = onArrival((id) => seen.push(id))
    markRemoteArrival('a', T)
    off()
    markRemoteArrival('b', T)
    expect(seen).toEqual(['a'])
  })
})
