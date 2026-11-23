/** Largest supported amount: R$ 999.999.999,99. */
export const MAX_CENTS = 99_999_999_999n

const API_AMOUNT = /^-?\d+(\.\d{1,2})?$/

/** What the app writes in when there is no family yet. */
export const DEFAULT_CURRENCY = 'BRL'
export const DEFAULT_LOCALE = 'pt-BR'

const formatters = new Map<string, Intl.NumberFormat>()

function formatterFor(locale: string, currency: string): Intl.NumberFormat {
  const key = `${locale}|${currency}`
  let formatter = formatters.get(key)
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { style: 'currency', currency })
    formatters.set(key, formatter)
  }
  return formatter
}

/** Parses the API decimal text (e.g. "1234.56") into bigint cents. */
export function parseCents(amount: string): bigint {
  if (!API_AMOUNT.test(amount)) {
    throw new Error('Invalid amount: expected an optional minus sign, digits and up to 2 decimals')
  }
  const negative = amount.startsWith('-')
  const [whole = '0', fraction = ''] = (negative ? amount.slice(1) : amount).split('.')
  const cents = BigInt(whole + fraction.padEnd(2, '0'))
  return negative ? -cents : cents
}

function decimalText(abs: bigint): `${number}` {
  const digits = abs.toString().padStart(3, '0')
  return `${digits.slice(0, -2)}.${digits.slice(-2)}` as `${number}`
}

/** Converts bigint cents into the API decimal text with 2 decimals. */
export function centsToApi(cents: bigint): string {
  return `${cents < 0n ? '-' : ''}${decimalText(cents < 0n ? -cents : cents)}`
}

/** Formats cents in the family's currency and locale; negatives use U+2212 before the amount. */
export function formatMoney(cents: bigint, currency: string, locale: string): string {
  const negative = cents < 0n
  const formatted = formatterFor(locale, currency).format(decimalText(negative ? -cents : cents))
  return negative ? `\u2212${formatted}` : formatted
}

function assertNotNegative(cents: bigint): void {
  if (cents < 0n) {
    throw new RangeError('cents must not be negative')
  }
}

/** Appends a typed digit; returns the input unchanged if it would exceed MAX_CENTS. */
export function appendDigit(cents: bigint, digit: number): bigint {
  assertNotNegative(cents)
  if (!Number.isInteger(digit) || digit < 0 || digit > 9) {
    throw new RangeError('digit must be an integer between 0 and 9')
  }
  const next = cents * 10n + BigInt(digit)
  return next <= MAX_CENTS ? next : cents
}

/** Implements the "00" key; returns the input unchanged if it would exceed MAX_CENTS. */
export function appendDoubleZero(cents: bigint): bigint {
  assertNotNegative(cents)
  const next = cents * 100n
  return next <= MAX_CENTS ? next : cents
}

/** Drops the last typed digit. */
export function removeLastDigit(cents: bigint): bigint {
  return cents / 10n
}

/** Smallest supported balance: the mirror of MAX_CENTS, for an account that owes. */
export const MIN_CENTS = -MAX_CENTS

/** Flips the sign: the keypad only types positive amounts, so a balance's sign comes apart from it. */
export function negateCents(cents: bigint): bigint {
  return -cents
}
