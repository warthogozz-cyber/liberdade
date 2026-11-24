import { describe, expect, it } from 'vitest'
import { formatRate, isRateInRange, parseRate, realReturn } from './realReturn'

describe('realReturn', () => {
  it('parsesCommaAndDot', () => {
    expect(parseRate('10')).toBe(100000n)
    expect(parseRate('4,5')).toBe(45000n)
    expect(parseRate('4.5')).toBe(45000n)
    expect(parseRate('-1,25')).toBe(-12500n)
  })

  it('rejectsGarbage', () => {
    for (const text of ['', 'abc', '1,23456', '1,2,3']) {
      expect(parseRate(text)).toBeNull()
    }
  })

  it('acceptsOnlyTheDocumentedRange', () => {
    expect(isRateInRange(parseRate('-99,99')!)).toBe(true)
    expect(isRateInRange(parseRate('1000')!)).toBe(true)
    expect(isRateInRange(parseRate('-100')!)).toBe(false)
    expect(isRateInRange(parseRate('1000,01')!)).toBe(false)
  })

  it('specExample', () => {
    expect(realReturn(100000n, 40000n)).toBe(577n)
  })

  it('belowInflationIsNegative', () => {
    expect(realReturn(30000n, 40000n)).toBe(-96n)
  })

  it('equalIsZero', () => {
    expect(realReturn(40000n, 40000n)).toBe(0n)
  })

  it('roundsHalfAwayFromZero', () => {
    // With inflation 0: (±50 × 10 000) / (1 000 000 + 0) = ±0.5 exactly, so it rounds away from zero.
    expect(realReturn(50n, 0n)).toBe(1n)
    expect(realReturn(-50n, 0n)).toBe(-1n)
  })

  it('formats', () => {
    expect(formatRate(577n)).toBe('5,77')
    expect(formatRate(-96n)).toBe('−0,96')
    expect(formatRate(0n)).toBe('0,00')
    expect(formatRate(123456n)).toBe('1234,56')
  })
})
