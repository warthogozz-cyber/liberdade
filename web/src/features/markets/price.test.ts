import { describe, expect, it } from 'vitest'
import { unitPriceToCents } from './price'

describe('unitPriceToCents', () => {
  it('roundsHalfUpToCents', () => {
    expect(unitPriceToCents('4.9950')).toBe(500n)
    expect(unitPriceToCents('4.9949')).toBe(499n)
    expect(unitPriceToCents('4.9900')).toBe(499n)
    expect(unitPriceToCents('12')).toBe(1200n)
    expect(unitPriceToCents('0.0050')).toBe(1n)
    expect(unitPriceToCents('0.0049')).toBe(0n)
  })

  it('refusesWhatIsNotADecimalPrice', () => {
    expect(() => unitPriceToCents('abc')).toThrow(RangeError)
    expect(() => unitPriceToCents('-1.00')).toThrow(RangeError)
    expect(() => unitPriceToCents('1.23456')).toThrow(RangeError)
  })
})
