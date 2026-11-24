import { afterEach, describe, expect, it } from 'vitest'
import type { ReserveView } from '@/shared/api/planning'
import { reserveReached, reserveStorageKey, shouldCelebrateReserve } from './reserveCelebration'

const view = (overrides: Partial<ReserveView>): ReserveView => ({
  balance: { amount: '18000.00', currency: 'BRL' },
  hasReserveAccounts: true,
  averageMonthlyCost: { amount: '3000.00', currency: 'BRL' },
  costSource: 'HISTORY',
  months: 6,
  targetMonths: 6,
  missing: { amount: '0.00', currency: 'BRL' },
  manualMonthlyCost: null,
  ...overrides,
})

afterEach(() => localStorage.clear())

describe('reserveCelebration', () => {
  it('reachedOnlyWithReserveAccounts', () => {
    expect(reserveReached(view({ hasReserveAccounts: false }))).toBe(false)
    expect(reserveReached(view({ months: 6 }))).toBe(true)
    expect(reserveReached(view({ months: null }))).toBe(false)
  })

  it('celebratesOnceUntilItDrops', () => {
    const ws = 'ws-once'
    expect(shouldCelebrateReserve(ws, view({ months: 6 }))).toBe(true)
    expect(localStorage.getItem(reserveStorageKey(ws))).not.toBeNull()
    expect(shouldCelebrateReserve(ws, view({ months: 7 }))).toBe(false)
    expect(shouldCelebrateReserve(ws, view({ months: 4 }))).toBe(false)
    expect(localStorage.getItem(reserveStorageKey(ws))).toBeNull()
    expect(shouldCelebrateReserve(ws, view({ months: 6 }))).toBe(true)
  })
})
