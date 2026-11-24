/**
 * The real return calculator. Rates are `bigint` percent × 10⁴ (100% = 1_000_000n), the result is
 * percent × 10². It is all integer maths: nothing passes through `Number`.
 */

const MINUS = '−'
const MIN_RATE = -999_900n // −99,99%
const MAX_RATE = 10_000_000n // 1000%

const RATE_PATTERN = /^([-−]?)(\d+)(?:[.,](\d{1,4}))?$/

/** "4,5" or "4.5" as 45000n; the sign may be "-" or the true minus U+2212. Null if unreadable. */
export function parseRate(text: string): bigint | null {
  const match = RATE_PATTERN.exec(text.trim())
  if (match === null) return null
  const [, sign, whole, fraction = ''] = match
  const value = BigInt(whole! + fraction.padEnd(4, '0'))
  return sign === '' ? value : -value
}

/** From −99,99% to 1000%. The floor keeps inflation away from −100%, so the divisor is positive. */
export function isRateInRange(rate: bigint): boolean {
  return rate >= MIN_RATE && rate <= MAX_RATE
}

function divideHalfAwayFromZero(numerator: bigint, denominator: bigint): bigint {
  const quotient = numerator / denominator
  const remainder = numerator % denominator
  const magnitude = remainder < 0n ? -remainder : remainder
  if (magnitude * 2n < denominator) return quotient
  return numerator < 0n ? quotient - 1n : quotient + 1n
}

/**
 * (1 + n) ÷ (1 + i) − 1 = (n − i) ÷ (1 + i), in one division rounded once. The inflation must be in
 * range, which keeps the divisor positive.
 */
export function realReturn(nominal: bigint, inflation: bigint): bigint {
  return divideHalfAwayFromZero((nominal - inflation) * 10_000n, 1_000_000n + inflation)
}

function withSign(negative: boolean, text: string): string {
  return negative ? `${MINUS}${text}` : text
}

/** 577n as "5,77", -96n as "−0,96". No thousands separator. */
export function formatRate(hundredths: bigint): string {
  const negative = hundredths < 0n
  const abs = negative ? -hundredths : hundredths
  const fraction = (abs % 100n).toString().padStart(2, '0')
  return withSign(negative, `${abs / 100n},${fraction}`)
}

/** An input rate for the formula line: two decimals, more only when the person typed them. */
export function formatInputRate(rate: bigint): string {
  const negative = rate < 0n
  const abs = negative ? -rate : rate
  const fraction = (abs % 10_000n).toString().padStart(4, '0').replace(/00$/, '')
  return withSign(negative, `${abs / 10_000n},${fraction.padEnd(2, '0')}`)
}
