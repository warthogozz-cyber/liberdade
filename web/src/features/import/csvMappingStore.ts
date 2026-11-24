import type { CsvMapping } from './csvDetect'

const KEY = 'liberdade.import.csvMapping.v1'

type Store = Record<string, Record<string, unknown>>

const isColumn = (v: unknown): v is number => typeof v === 'number' && Number.isInteger(v)

function isMapping(v: unknown): v is CsvMapping {
  if (typeof v !== 'object' || v === null) return false
  const m = v as Record<string, unknown>
  const a = m.amount as Record<string, unknown> | null | undefined
  const amountOk =
    typeof a === 'object' &&
    a !== null &&
    ((a.kind === 'signed' && isColumn(a.column)) ||
      (a.kind === 'split' && isColumn(a.debit) && isColumn(a.credit)))
  return (
    isColumn(m.headerRow) &&
    isColumn(m.date) &&
    isColumn(m.description) &&
    amountOk &&
    (m.decimal === ',' || m.decimal === '.')
  )
}

function readStore(): Store {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '{}')
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? (parsed as Store)
      : {}
  } catch {
    return {}
  }
}

/** The column mapping remembered for this account and header, or null. Never throws. */
export function loadMapping(accountId: string, signature: string): CsvMapping | null {
  const store = readStore()
  if (!Object.hasOwn(store, accountId)) return null
  const forAccount = store[accountId]
  if (typeof forAccount !== 'object' || forAccount === null) return null
  if (!Object.hasOwn(forAccount, signature)) return null
  const found = forAccount[signature]
  return isMapping(found) ? found : null
}

/** Remembers the mapping on this device only; without storage it is simply not remembered. */
export function saveMapping(accountId: string, signature: string, mapping: CsvMapping): void {
  try {
    const store = readStore()
    const forAccount = Object.hasOwn(store, accountId) ? (store[accountId] ?? {}) : {}
    localStorage.setItem(
      KEY,
      JSON.stringify({ ...store, [accountId]: { ...forAccount, [signature]: mapping } }),
    )
  } catch {
    // Without storage the person maps the columns again next time, which is the safe side.
  }
}

/** A remembered mapping's columns and decimal on top of this file's own header row, which moves with the title rows. */
export function applyStoredMapping(detected: CsvMapping, stored: CsvMapping): CsvMapping {
  return { ...stored, headerRow: detected.headerRow }
}
