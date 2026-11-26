/**
 * A unit price as the API sends it (up to 4 decimals, as text) in whole cents, rounded HALF_UP, so
 * `formatMoney` shows what the family reads on the shelf (plan 10.2, D18).
 */
export function unitPriceToCents(text: string): bigint {
  const match = /^(\d+)(?:\.(\d{1,4}))?$/.exec(text)
  if (!match) throw new RangeError(`Not a unit price: ${text}`)
  const tenThousandths = BigInt(match[1]!) * 10000n + BigInt((match[2] ?? '').padEnd(4, '0'))
  return (tenThousandths + 50n) / 100n
}
