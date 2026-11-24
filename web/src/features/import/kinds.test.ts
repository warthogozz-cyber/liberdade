import { describe, expect, it } from 'vitest'
import { detectCsv, linesFromCsv } from './csvDetect'
import { parseCsv } from './readers/csv'
import { decode } from './readers/decode'
import { fixtureBytes } from './readers/fixtures.test-util'
import { readOfx } from './readers/ofx'
import { assignKinds } from './kinds'
import type { StatementLine } from './readers/types'

const line = (cents: bigint, description = 'X'): StatementLine => ({
  occurredOn: '2026-11-03',
  cents,
  description,
})

function cardCsvLines(text: string): StatementLine[] {
  const parsed = parseCsv(text)
  return linesFromCsv(parsed.rows, detectCsv(parsed).mapping).lines
}

describe('assignKinds', () => {
  it('bySignOutsideCards', () => {
    const out = assignKinds([line(-1000n), line(1000n), line(0n)], false)
    expect(out.map((l) => [l.kind, l.absCents, l.billPayment])).toEqual([
      ['EXPENSE', 1000n, false],
      ['INCOME', 1000n, false],
    ])
  })

  it('cardPositivePurchases', async () => {
    const lines = cardCsvLines(decode(await fixtureBytes('csv-card-positive-purchases.csv')))
    const out = assignKinds(lines, true)
    expect(out.map((l) => l.kind)).toEqual(['EXPENSE', 'EXPENSE', 'EXPENSE', 'INCOME'])
    expect(out.map((l) => l.billPayment)).toEqual([false, false, false, true])
    expect(out.map((l) => l.absCents)).toEqual([4590n, 12000n, 3340n, 19930n])
  })

  it('cardTieMeansNegativePurchases', () => {
    const out = assignKinds([line(500n), line(700n), line(-300n), line(-900n)], true)
    expect(out.map((l) => l.kind)).toEqual(['INCOME', 'INCOME', 'EXPENSE', 'EXPENSE'])
    expect(out.map((l) => l.billPayment)).toEqual([true, true, false, false])
  })

  it('cardOppositeSignComesUncheckedAsIncome', async () => {
    const lines = readOfx(decode(await fixtureBytes('ofx-card-negative-purchases.ofx')))
    const out = assignKinds(lines, true)
    expect(out.map((l) => l.kind)).toEqual(['EXPENSE', 'EXPENSE', 'EXPENSE', 'INCOME'])
    expect(out[3]?.billPayment).toBe(true)
    expect(out[3]?.absCents).toBe(19930n)
    expect(out.slice(0, 3).every((l) => !l.billPayment)).toBe(true)
  })

  it('cardDropsZeroBeforeCounting', () => {
    const zeros = [line(0n), line(0n), line(0n)]
    const out = assignKinds([...zeros, line(500n), line(-100n), line(-200n)], true)
    expect(out.map((l) => l.kind)).toEqual(['INCOME', 'EXPENSE', 'EXPENSE'])
  })
})
