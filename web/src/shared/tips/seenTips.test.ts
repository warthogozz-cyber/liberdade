import { afterEach, describe, expect, it, vi } from 'vitest'
import { hasSeenTip, markTipSeen } from './seenTips'

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('seenTips', () => {
  it('seenPerPerson', () => {
    expect(hasSeenTip('u1', 'transactions')).toBe(false)
    markTipSeen('u1', 'transactions')
    expect(hasSeenTip('u1', 'transactions')).toBe(true)
    expect(hasSeenTip('u2', 'transactions')).toBe(false)
    expect(hasSeenTip('u1', 'accounts')).toBe(false)
    expect(localStorage.getItem('liberdade.tip:u1:transactions')).toBe('1')
  })

  it('notSeenWhenStorageThrows', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(hasSeenTip('u1', 'dashboard')).toBe(false)
    expect(() => markTipSeen('u1', 'dashboard')).not.toThrow()
  })
})
