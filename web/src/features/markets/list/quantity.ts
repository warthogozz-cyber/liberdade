/** A quantity as the API takes it: up to 15 digits and 4 decimals, as text. */
const QUANTITY = /^(\d{1,15})(?:\.(\d{1,4}))?$/
const SCALE = 10000n

/** What the person typed (a comma or a dot) as the API's decimal text, or null when it is not a quantity above zero. */
export function parseQuantity(typed: string): string | null {
  const text = typed.trim().replace(',', '.')
  const match = QUANTITY.exec(text)
  if (!match) return null
  const [, whole, decimals] = match
  return BigInt(whole!) * SCALE + BigInt((decimals ?? '').padEnd(4, '0')) > 0n ? text : null
}

/** The quantity plus `step` (1 or -1), without floating point; null when it would not stay above zero. */
export function stepQuantity(quantity: string, step: 1 | -1): string | null {
  const match = QUANTITY.exec(quantity)
  if (!match) return null
  const scaled =
    BigInt(match[1]!) * SCALE + BigInt((match[2] ?? '').padEnd(4, '0')) + BigInt(step) * SCALE
  if (scaled <= 0n) return null
  const decimals = (scaled % SCALE).toString().padStart(4, '0').replace(/0+$/, '')
  const whole = (scaled / SCALE).toString()
  const next = decimals === '' ? whole : `${whole}.${decimals}`
  return QUANTITY.test(next) ? next : null
}

/** The quantity as the person reads it: a comma for the decimals. */
export const showQuantity = (quantity: string) => quantity.replace('.', ',')
