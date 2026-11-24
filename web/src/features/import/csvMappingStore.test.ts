import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CsvMapping } from './csvDetect'
import { loadMapping, saveMapping } from './csvMappingStore'

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
})
