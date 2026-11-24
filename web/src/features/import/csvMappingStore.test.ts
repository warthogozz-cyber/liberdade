import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CsvMapping } from './csvDetect'
import { detectCsv, linesFromCsv } from './csvDetect'
import { applyStoredMapping, loadMapping, saveMapping } from './csvMappingStore'

const mapping: CsvMapping = {
  headerRow: 1,
  date: 0,
  description: 1,
  amount: { kind: 'split', debit: 2, credit: 3 },
  decimal: ',',
}

beforeEach(() => localStorage.clear())
afterEach(() => vi.restoreAllMocks())

describe('csvMappingStore', () => {
  it('perAccountAndSignature', () => {
    saveMapping('A', 'S', mapping)
    expect(loadMapping('A', 'S')).toEqual(mapping)
    expect(loadMapping('B', 'S')).toBeNull()
    expect(loadMapping('A', 'T')).toBeNull()
  })

  it('keepsOtherEntriesWhenSaving', () => {
    saveMapping('A', 'S', mapping)
    saveMapping('A', 'T', { ...mapping, date: 3 })
    saveMapping('B', 'S', mapping)
    expect(loadMapping('A', 'S')).toEqual(mapping)
    expect(loadMapping('A', 'T')?.date).toBe(3)
    expect(loadMapping('B', 'S')).toEqual(mapping)
  })

  it('survivesBrokenStorage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(loadMapping('A', 'S')).toBeNull()
    expect(() => saveMapping('A', 'S', mapping)).not.toThrow()
  })

  it('corruptOrForeignContentReadsAsNull', () => {
    localStorage.setItem('liberdade.import.csvMapping.v1', '{not json')
    expect(loadMapping('A', 'S')).toBeNull()
    localStorage.setItem(
      'liberdade.import.csvMapping.v1',
      JSON.stringify({ A: { S: { date: 'x' } } }),
    )
    expect(loadMapping('A', 'S')).toBeNull()
    saveMapping('A', 'S', mapping)
    expect(loadMapping('A', 'S')).toEqual(mapping)
  })

  it('doesNotReadInheritedKeys', () => {
    saveMapping('A', 'S', mapping)
    expect(loadMapping('constructor', 'S')).toBeNull()
    expect(loadMapping('A', 'toString')).toBeNull()
  })

  it('applyStoredMappingKeepsTheFreshHeaderRow', () => {
    const stored: CsvMapping = { ...mapping, headerRow: 3, date: 1, decimal: '.' }
    const applied = applyStoredMapping({ ...mapping, headerRow: 1 }, stored)
    expect(applied).toEqual({ ...stored, headerRow: 1 })
  })

  it('fewerTitleRowsStillYieldEveryRow', () => {
    const data = [
      ['03/11/2026', 'A', '-1,00'],
      ['04/11/2026', 'B', '-2,00'],
    ]
    const header = ['Data', 'Descricao', 'Valor']
    const before = [['Extrato', '', ''], ['Periodo', '', ''], ['Conta', '', ''], header, ...data]
    const after = [['Extrato', '', ''], header, ...data]
    const saved = detectCsv({ separator: ';', rows: before }).mapping
    expect(saved.headerRow).toBe(3)
    const fresh = detectCsv({ separator: ';', rows: after }).mapping
    expect(fresh.headerRow).toBe(1)
    const applied = applyStoredMapping(fresh, saved)
    expect(linesFromCsv(after, applied)).toHaveLength(2)
    expect(() => linesFromCsv(after, saved)).toThrow()
  })
})
