import { afterEach, describe, expect, it, vi } from 'vitest'
import { DEFAULT_TIMEZONE, addDays, currentMonth, localDate } from '@/shared/i18n'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('localDate and currentMonth', () => {
  it('uses Fortaleza by default', () => {
    expect(DEFAULT_TIMEZONE).toBe('America/Fortaleza')
  })

  it.each([
    ['2026-11-03T02:30:00Z', '2026-11-02'],
    ['2026-12-01T02:59:59Z', '2026-11-30'],
  ])('gives the family’s date at %s, not the UTC one', (instant, expected) => {
    expect(localDate(new Date(instant), 'America/Fortaleza')).toBe(expected)
  })

  it.each([
    ['2026-12-01T02:59:59Z', '2026-11'],
    ['2026-12-01T03:00:00Z', '2026-12'],
  ])('turns the month at local midnight: %s is %s', (instant, expected) => {
    expect(currentMonth(new Date(instant), 'America/Fortaleza')).toBe(expected)
  })

  it('builds the date from its parts, not from the locale’s pattern', () => {
    // `format` is a getter on the prototype that hands back the formatting function.
    const prototype: { format: unknown } = Intl.DateTimeFormat.prototype
    vi.spyOn(prototype, 'format', 'get').mockReturnValue(() => '11/02/2026')

    expect(localDate(new Date('2026-11-03T02:30:00Z'), 'America/Fortaleza')).toBe('2026-11-02')
  })
})

describe('addDays', () => {
  it.each([
    ['2026-03-01', -1, '2026-02-28'],
    ['2026-12-31', 1, '2027-01-01'],
  ])('%s plus %i days is %s', (date, days, expected) => {
    expect(addDays(date, days)).toBe(expected)
  })
})
