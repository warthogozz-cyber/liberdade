import { describe, expect, it } from 'vitest'
import { parseCsv } from './csv'
import { fixtureBytes } from './fixtures.test-util'
import { decode } from './decode'

describe('parseCsv', () => {
  it('quotedFields', async () => {
    expect(parseCsv('"a;b";"c""d"').rows).toEqual([['a;b', 'c"d']])
  })

  it('keepsNewlinesInsideQuotes', async () => {
    expect(parseCsv('a;"b\nc";d\r\ne;f;g\r\n').rows).toEqual([
      ['a', 'b\nc', 'd'],
      ['e', 'f', 'g'],
    ])
  })

  it('quoteOnlyOpensAtFieldStart', () => {
    const parsed = parseCsv('a;5" TV;c\n1;2;3')
    expect(parsed.separator).toBe(';')
    expect(parsed.rows).toEqual([
      ['a', '5" TV', 'c'],
      ['1', '2', '3'],
    ])
  })

  it('findsTheSeparator', async () => {
    expect(parseCsv('a;b;c\n1;2,5;3\n4;5;6').separator).toBe(';')
    expect(parseCsv('a,b,c\n1,2,3\n4,5,6').separator).toBe(',')
    expect(parseCsv('a\tb\tc\n1\t2,5\t3\n4\t5\t6').separator).toBe('\t')
  })

  it('skipsEmptyLinesAndIgnoresTitleLines', async () => {
    const { separator, rows } = parseCsv(
      decode(await fixtureBytes('csv-semicolon-comma-decimal.csv'), 'csv'),
    )
    expect(separator).toBe(';')
    expect(rows).toHaveLength(7)
    expect(rows[2]).toEqual(['Data', 'Descrição', 'Valor', 'Saldo'])
    expect(rows[3]).toEqual(['03/11/2026', 'PADARIA PAO QUENTE', '-45,90', '1.954,10'])
  })
})
