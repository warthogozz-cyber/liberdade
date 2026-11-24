import { describe, expect, it } from 'vitest'
import { detectCsv, linesFromCsv } from './csvDetect'
import { parseCsv } from './readers/csv'
import { decode } from './readers/decode'
import { fixtureBytes } from './readers/fixtures.test-util'
import { readOfx } from './readers/ofx'
import type { StatementLine } from './readers/types'
import { importKeys } from './importKey'
import { assignKinds } from './kinds'
import type { KindedLine } from './kinds'

const ACCOUNT = '0192f5a0-0000-7000-8000-000000000001'

const expense = (absCents: bigint, description: string): KindedLine => ({
  occurredOn: '2026-11-03',
  cents: -absCents,
  description,
  kind: 'EXPENSE',
  absCents,
  billPayment: false,
})

function csvLines(text: string): StatementLine[] {
  const parsed = parseCsv(text)
  return linesFromCsv(parsed.rows, detectCsv(parsed).mapping)
}

describe('importKeys', () => {
  it('fixedVector', async () => {
    const keys = await importKeys(ACCOUNT, [expense(4590n, 'Padaria Pão Quente')])
    expect(keys).toEqual(['11761bb90289f327bb7434f27739e1c45d3b89f7c66311d810e433a290153f15'])
  })

  it('trimsAndCutsTheDescriptionAt140', async () => {
    const [a] = await importKeys(ACCOUNT, [expense(100n, '  ' + 'a'.repeat(200))])
    const [b] = await importKeys(ACCOUNT, [expense(100n, 'a'.repeat(140))])
    expect(a).toBe(b)
  })

  it('identicalLinesGetN', async () => {
    const a = expense(1250n, 'MERCADO CENTRAL')
    const b = expense(300n, 'OUTRO')
    const keys = await importKeys(ACCOUNT, [a, a, b])
    expect(new Set(keys).size).toBe(3)
    const reordered = await importKeys(ACCOUNT, [b, a, a])
    expect(new Set(reordered)).toEqual(new Set(keys))
  })

  it('sameKeyFromOfxAndCsv', async () => {
    const ofx = readOfx(decode(await fixtureBytes('ofx-sgml-1252.ofx'), 'ofx'))
    const csv = csvLines(decode(await fixtureBytes('csv-semicolon-comma-decimal.csv'), 'csv'))
    expect(csv).toHaveLength(4)
    const fromOfx = await importKeys(ACCOUNT, assignKinds(ofx, false))
    const fromCsv = await importKeys(ACCOUNT, assignKinds(csv, false))
    expect(fromCsv).toEqual(fromOfx)
    expect(new Set(fromOfx).size).toBe(4)
  })

  it('cardKeysMatchAcrossConventions', async () => {
    const build = (sign: bigint) =>
      assignKinds(
        [
          { occurredOn: '2026-11-03', cents: 4590n * sign, description: 'PADARIA' },
          { occurredOn: '2026-11-04', cents: 100n * sign, description: 'B' },
          { occurredOn: '2026-11-05', cents: 200n * sign, description: 'C' },
        ],
        true,
      )
    expect(await importKeys(ACCOUNT, build(1n))).toEqual(await importKeys(ACCOUNT, build(-1n)))
  })
})
