import { describe, expect, it } from 'vitest'
import { decimalEvidence, detectCsv, linesFromCsv } from './csvDetect'
import { parseCsv } from './readers/csv'
import { decode } from './readers/decode'
import { fixtureBytes } from './readers/fixtures.test-util'
import { MAX_STATEMENT_LINES, StatementReadError } from './readers/types'

async function fixture(name: string) {
  return parseCsv(decode(await fixtureBytes(name)))
}

describe('detectCsv', () => {
  it('semicolonCommaDecimal', async () => {
    const parsed = await fixture('csv-semicolon-comma-decimal.csv')
    const found = detectCsv(parsed)
    expect(found.certain).toBe(true)
    expect(found.mapping).toEqual({
      headerRow: 2,
      date: 0,
      description: 1,
      amount: { kind: 'signed', column: 2 },
      decimal: ',',
    })
    expect(found.signature).toBe('data|descricao|valor|saldo|;')
    const lines = linesFromCsv(parsed.rows, found.mapping).lines
    expect(lines.map((l) => [l.occurredOn, l.cents, l.description])).toEqual([
      ['2026-11-03', -4590n, 'PADARIA PAO QUENTE'],
      ['2026-11-04', -1250n, 'MERCADO CENTRAL'],
      ['2026-11-04', -1250n, 'MERCADO CENTRAL'],
      ['2026-11-05', 10000n, 'Doação mensal'],
    ])
  })

  it('debitCreditColumns', async () => {
    const parsed = await fixture('csv-debit-credit-columns-1252.csv')
    const found = detectCsv(parsed)
    expect(found.certain).toBe(true)
    expect(found.mapping.amount).toEqual({ kind: 'split', debit: 2, credit: 3 })
    expect(found.mapping.description).toBe(1)
    const lines = linesFromCsv(parsed.rows, found.mapping).lines
    expect(lines.map((l) => l.cents)).toEqual([-4590n, 10000n, -7800n])
    expect(lines[2]?.description).toBe('AÇOUGUE SÃO JOÃO')
  })

  it('isoDatesAndDot', () => {
    const rows = [
      ['date', 'description', 'amount'],
      ['2026-11-03', 'Coffee shop', '-4.50'],
      ['2026-11-04', 'Salary', '1200.00'],
      ['2026-11-05', 'Books', '-30'],
    ]
    const found = detectCsv({ separator: ',', rows })
    expect(found.certain).toBe(true)
    expect(found.mapping.decimal).toBe('.')
    expect(linesFromCsv(rows, found.mapping).lines.map((l) => [l.occurredOn, l.cents])).toEqual([
      ['2026-11-03', -450n],
      ['2026-11-04', 120000n],
      ['2026-11-05', -3000n],
    ])
  })

  it('dateColumnNeedsNinetyPercent', () => {
    const rows = [
      ['Data', 'Descricao', 'Valor'],
      ['03/11/2026', 'PIX 03/10', '-10,00'],
      ['04/11/2026', '05/11/2026', '-20,00'],
      ['05/11/2026', 'PIX 04/10', '-30,00'],
    ]
    const found = detectCsv({ separator: ';', rows })
    expect(found.mapping.date).toBe(0)
    expect(found.mapping.description).toBe(1)
    expect(linesFromCsv(rows, found.mapping).lines).toHaveLength(3)
  })

  it('unknownHeaderNeedsMapping', () => {
    const rows = [
      ['Quando', 'Quem', 'Quanto', 'Resto'],
      ['03/11/2026', 'PADARIA', '-45,90', '100,00'],
      ['04/11/2026', 'MERCADO', '-12,50', '87,50'],
    ]
    expect(detectCsv({ separator: ';', rows }).certain).toBe(false)
  })

  it('saldoIsNeverTheValue', () => {
    const rows = [
      ['Data', 'Descricao', 'Saldo', 'Valor'],
      ['03/11/2026', 'PADARIA', '100,00', '-45,90'],
      ['04/11/2026', 'MERCADO', '87,50', '-12,50'],
    ]
    expect(detectCsv({ separator: ';', rows }).mapping.amount).toEqual({
      kind: 'signed',
      column: 3,
    })
  })

  it('commaDecimalWithoutCents', async () => {
    const parsed = await fixture('csv-comma-decimal-no-cents.csv')
    const found = detectCsv(parsed)
    expect(found.certain).toBe(true)
    expect(found.mapping.decimal).toBe(',')
    const { lines, skipped } = linesFromCsv(parsed.rows, found.mapping)
    expect(lines.map((l) => [l.cents, l.description])).toEqual([
      [-4590n, 'PADARIA PAO QUENTE'],
      [-1250n, 'MERCADO CENTRAL'],
      [-123400n, 'ALUGUEL'],
      [10000n, 'Doação mensal'],
    ])
    expect(skipped).toBe(0)
  })

  it('onlyAmbiguousThousandsIsNotCertain', () => {
    const rows = [
      ['Data', 'Descricao', 'Valor'],
      ['03/11/2026', 'ALUGUEL', '-1.234'],
      ['04/11/2026', 'SALARIO', '2.500'],
    ]
    const found = detectCsv({ separator: ';', rows })
    expect(found.mapping.amount).toEqual({ kind: 'signed', column: 2 })
    expect(found.certain).toBe(false)
  })

  it('shortYearsAndDateTimesKeepTheDate', () => {
    const rows = [
      ['Data', 'Descricao', 'Valor'],
      ['03/11/26', 'A', '-1,00'],
      ['04/11/2026 14:35', 'B', '-2,00'],
      ['05/11/2026 08:01:59', 'C', '-3,00'],
    ]
    const found = detectCsv({ separator: ';', rows })
    expect(found.certain).toBe(true)
    expect(linesFromCsv(rows, found.mapping).lines.map((l) => l.occurredOn)).toEqual([
      '2026-11-03',
      '2026-11-04',
      '2026-11-05',
    ])
    const iso = [
      ['date', 'description', 'amount'],
      ['2026-11-03T14:35', 'A', '-1.00'],
      ['2026-11-04T08:01:59', 'B', '-2.00'],
      ['2026-11-05 10:00:00', 'C', '-3.00'],
    ]
    const isoFound = detectCsv({ separator: ',', rows: iso })
    expect(isoFound.mapping.date).toBe(0)
    expect(linesFromCsv(iso, isoFound.mapping).lines.map((l) => l.occurredOn)).toEqual([
      '2026-11-03',
      '2026-11-04',
      '2026-11-05',
    ])
  })

  it('headerlessFileGetsAColumnCountSignature', () => {
    const rows = [
      ['03/11/2026', 'PADARIA', '-45,90'],
      ['04/11/2026', 'MERCADO CENTRAL', '-12,50'],
    ]
    const found = detectCsv({ separator: ';', rows })
    expect(found.mapping.headerRow).toBe(-1)
    expect(found.signature).toBe('#cols:3|;')
    expect(linesFromCsv(rows, found.mapping).lines).toHaveLength(2)
  })
})

describe('decimalEvidence', () => {
  const evidence = (...cells: string[]) =>
    decimalEvidence(
      cells.map((cell) => [cell]),
      [0],
    )

  it('readsTheThousandsCases', () => {
    expect(evidence('45,9')).toEqual({ decimal: ',', certain: true })
    expect(evidence('1.234,56')).toEqual({ decimal: ',', certain: true })
    expect(evidence('1,234.56')).toEqual({ decimal: '.', certain: true })
    expect(evidence('12.34')).toEqual({ decimal: '.', certain: true })
    expect(evidence('1.234.567')).toEqual({ decimal: ',', certain: true })
    expect(evidence('1.234', '45,9')).toEqual({ decimal: ',', certain: true })
    expect(evidence('1,234', '12.34')).toEqual({ decimal: '.', certain: true })
  })

  it('anAmbiguousCellAloneIsNotCertain', () => {
    expect(evidence('1.234')).toEqual({ decimal: ',', certain: false })
    expect(evidence('1,234')).toEqual({ decimal: '.', certain: false })
    expect(evidence('45,90', '12.34')).toMatchObject({ certain: false })
  })

  it('wholeNumbersAreCertain', () => {
    expect(evidence('100', '-45')).toEqual({ decimal: ',', certain: true })
  })
})

describe('linesFromCsv', () => {
  const mapping = {
    headerRow: 0,
    date: 0,
    description: 1,
    amount: { kind: 'signed', column: 2 },
    decimal: ',',
  } as const

  it('stripsCurrencyAndThousands', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['03/11/2026', 'A', 'R$ 1.234,56'],
    ]
    expect(linesFromCsv(rows, mapping).lines[0]?.cents).toBe(123456n)
  })

  it('padsFractionAndReadsSign', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['03/11/2026', 'A', '1,5'],
      ['03/11/2026', 'B', '-R$ 2'],
      ['03/11/2026', 'C', '7,'],
    ]
    expect(linesFromCsv(rows, mapping).lines.map((l) => l.cents)).toEqual([150n, -200n, 700n])
  })

  it('skipsRowsWithMoreThanTwoFractionDigitsOrNoAmount', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['03/11/2026', 'A', '1,234'],
      ['03/11/2026', 'B', 'abc'],
      ['Saldo final', 'C', '9,00'],
      ['03/11/2026', 'D', '5,00'],
    ]
    expect(linesFromCsv(rows, mapping).lines.map((l) => l.description)).toEqual(['D'])
  })

  it('thousandsOnlyBeforeGroupsOfThree', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['03/11/2026', 'A', '1.234'],
      ['03/11/2026', 'B', '12.34'],
      ['03/11/2026', 'C', '1.23.456'],
      ['03/11/2026', 'D', '1.234.567,8'],
      ['03/11/2026', 'E', '45,9'],
    ]
    const { lines, skipped } = linesFromCsv(rows, mapping)
    expect(lines.map((l) => [l.description, l.cents])).toEqual([
      ['A', 123400n],
      ['D', 123456780n],
      ['E', 4590n],
    ])
    expect(skipped).toBe(2)
    const dot = linesFromCsv(
      [
        ['d', 'x', 'v'],
        ['03/11/2026', 'A', '1,234.56'],
        ['03/11/2026', 'B', '12,34'],
      ],
      { ...mapping, decimal: '.' },
    )
    expect(dot.lines.map((l) => l.cents)).toEqual([123456n])
    expect(dot.skipped).toBe(1)
  })

  it('countsTheSkippedRows', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['03/11/2026', 'A', '1,00'],
      ['Total', '', '1,00'],
      ['04/11/2026', 'B', ''],
      ['05/11/2026', 'C', '2,00'],
    ]
    const { lines, skipped } = linesFromCsv(rows, mapping)
    expect(lines.map((l) => l.description)).toEqual(['A', 'C'])
    expect(skipped).toBe(2)
  })

  it('keepsBalanceRowsForThePreview', async () => {
    const parsed = await fixture('csv-balance-rows.csv')
    const found = detectCsv(parsed)
    expect(found.certain).toBe(true)
    const { lines, skipped } = linesFromCsv(parsed.rows, found.mapping)
    expect(lines).toHaveLength(5)
    expect(skipped).toBe(0)
  })

  it('dotDecimalDropsTheCommaThousands', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['03/11/2026', 'A', '1,234.56'],
    ]
    expect(linesFromCsv(rows, { ...mapping, decimal: '.' }).lines[0]?.cents).toBe(123456n)
  })

  it('splitMakesDebitNegativeAndCreditPositive', () => {
    const rows = [
      ['d', 'x', 'out', 'in'],
      ['03/11/2026', 'A', '10,00', ''],
      ['04/11/2026', 'B', '', '20,00'],
      ['05/11/2026', 'C', '', ''],
    ]
    const lines = linesFromCsv(rows, {
      ...mapping,
      amount: { kind: 'split', debit: 2, credit: 3 },
    }).lines
    expect(lines.map((l) => l.cents)).toEqual([-1000n, 2000n])
  })

  it('rejectsImpossibleDates', () => {
    const rows = [
      ['d', 'x', 'v'],
      ['31/02/2026', 'A', '1,00'],
      ['03/11/2026', 'B', '1,00'],
    ]
    expect(linesFromCsv(rows, mapping).lines.map((l) => l.description)).toEqual(['B'])
  })

  it('emptyAndTooLargeAreErrors', () => {
    expect(() => linesFromCsv([['d', 'x', 'v']], mapping)).toThrow(StatementReadError)
    const many = [
      ['d', 'x', 'v'],
      ...Array.from({ length: MAX_STATEMENT_LINES + 1 }, () => ['03/11/2026', 'A', '1,00']),
    ]
    try {
      linesFromCsv(many, mapping)
      expect.unreachable()
    } catch (e) {
      expect((e as StatementReadError).reason).toBe('tooLarge')
    }
  })
})
