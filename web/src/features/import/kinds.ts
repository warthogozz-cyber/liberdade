import type { StatementLine } from './readers/types'

export type KindedLine = StatementLine & {
  kind: 'INCOME' | 'EXPENSE'
  /** Always positive: the kind carries the direction. */
  absCents: bigint
  /** A card line with the sign opposite to the purchases, most likely the bill being paid. */
  billPayment: boolean
}

/**
 * Gives each line its kind and drops the zero-value ones. On an ordinary account the sign decides. On a card
 * the most common sign is the purchases' (a tie means negative): they are expenses whatever the file's sign,
 * and the opposite sign comes back as an income flagged as a possible bill payment.
 */
export function assignKinds(lines: StatementLine[], isCard: boolean): KindedLine[] {
  const nonZero = lines.filter((l) => l.cents !== 0n)
  const positives = nonZero.filter((l) => l.cents > 0n).length
  const negatives = nonZero.length - positives
  const purchasesAreNegative = !isCard || negatives >= positives

  return nonZero.map((l) => {
    const negative = l.cents < 0n
    const absCents = negative ? -l.cents : l.cents
    if (!isCard) {
      return { ...l, kind: negative ? 'EXPENSE' : 'INCOME', absCents, billPayment: false }
    }
    const purchase = negative === purchasesAreNegative
    return { ...l, kind: purchase ? 'EXPENSE' : 'INCOME', absCents, billPayment: !purchase }
  })
}
