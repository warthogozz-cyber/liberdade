import { describe, expect, it } from 'vitest'
import { MAX_CENTS, appendDigit, centsToApi, formatBRL, parseCents, removeLastDigit } from './money'

const NBSP = '\u00A0'
const MINUS = '\u2212'

describe('formatBRL', () => {
  it('formats PRD 7.1 total', () => expect(formatBRL(476544n)).toBe(`R$${NBSP}4.765,44`))
  it('formats negative with U+2212', () => expect(formatBRL(-8790n)).toBe(`${MINUS}R$${NBSP}87,90`))
  it('formats beyond 2^53 exactly', () =>
    expect(formatBRL(123456789012345678n)).toBe(`R$${NBSP}1.234.567.890.123.456,78`))
  it.each([
    [0n, `R$${NBSP}0,00`],
    [1234n, `R$${NBSP}12,34`],
  ])('formats %s', (cents, expected) => expect(formatBRL(cents)).toBe(expected))
})

describe('parseCents', () => {
  it.each([
    ['1234.56', 123456n],
    ['1234.5', 123450n],
    ['-0.05', -5n],
    ['0', 0n],
    ['-0.5', -50n],
    ['-0', 0n],
    ['-0.00', 0n],
  ])('parses %s', (text, expected) => expect(parseCents(text)).toBe(expected))

  it.each(['1.234,56', '1e3', '12.345', '', ' 5', '+5'])('rejects %j', (text) =>
    expect(() => parseCents(text)).toThrow(Error),
  )

  it('does not echo the input in the error message', () =>
    expect(() => parseCents('abc')).toThrow(/^(?!.*abc)/))
})

describe('centsToApi', () => {
  it.each([
    [123456n, '1234.56'],
    [-5n, '-0.05'],
    [0n, '0.00'],
  ])('converts %s', (cents, expected) => expect(centsToApi(cents)).toBe(expected))

  it.each([-99999n, 1n, MAX_CENTS])('round-trips %s', (cents) =>
    expect(parseCents(centsToApi(cents))).toBe(cents),
  )
})

describe('appendDigit', () => {
  it('types 1,2,3,4 as R$ 12,34', () =>
    expect([1, 2, 3, 4].reduce((c, d) => appendDigit(c, d), 0n)).toBe(1234n))
  it('ignores a digit past MAX_CENTS', () =>
    expect(appendDigit(10_000_000_000n, 0)).toBe(10_000_000_000n))
  it('reaches MAX_CENTS exactly', () => expect(appendDigit(9_999_999_999n, 9)).toBe(MAX_CENTS))
  it('keeps MAX_CENTS', () => expect(appendDigit(MAX_CENTS, 9)).toBe(MAX_CENTS))
  it.each([10, -1, 1.5])('throws RangeError for %s', (digit) =>
    expect(() => appendDigit(0n, digit)).toThrow(RangeError),
  )
})

describe('removeLastDigit', () => {
  it.each([
    [1234n, 123n],
    [0n, 0n],
  ])('removes from %s', (cents, expected) => expect(removeLastDigit(cents)).toBe(expected))
})
