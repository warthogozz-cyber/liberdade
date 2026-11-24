import { normalizeDescription } from './normalize'
import type { CsvSeparator } from './readers/csv'
import { MAX_STATEMENT_LINES, StatementReadError, type StatementLine } from './readers/types'

export type CsvMapping = {
  /** Index of the header row in `rows`, or -1 when the file has none. Rows up to it are never data. */
  headerRow: number
  date: number
  description: number
  amount: { kind: 'signed'; column: number } | { kind: 'split'; debit: number; credit: number }
  decimal: ',' | '.'
}

export type CsvDetection = { mapping: CsvMapping; certain: boolean; signature: string }

const DATE = /^(\d{2})\/(\d{2})\/(\d{4})$|^(\d{4})-(\d{2})-(\d{2})$/
const MIN_DATE_SHARE = 0.9
const SIGNED_NAMES = ['valor', 'amount', 'value']
const DEBIT_NAMES = ['debito', 'saida']
const CREDIT_NAMES = ['credito', 'entrada']
const DESCRIPTION_NAMES = ['descricao', 'historico', 'lancamento', 'memo', 'description']
const NUMERIC = /^[-+]?\s*(?:R\$)?\s*[-+]?[\d.,\s]*\d[\d.,\s]*$/i

/** The cell as yyyy-MM-dd, or null when it is not a real calendar date in one of the two accepted layouts. */
function parseDate(cell: string | undefined): string | null {
  const match = DATE.exec((cell ?? '').trim())
  if (!match) return null
  const [year = '', month = '', day = ''] = match[1]
    ? [match[3], match[2], match[1]]
    : [match[4], match[5], match[6]]
  const probe = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)))
  const real =
    probe.getUTCFullYear() === Number(year) &&
    probe.getUTCMonth() === Number(month) - 1 &&
    probe.getUTCDate() === Number(day)
  return real ? `${year}-${month}-${day}` : null
}

/**
 * Cents of an amount cell, or null when it is not an amount (the row is then no data row). The thousands
 * character is the one that is not the decimal; at most two fraction digits are accepted, padded.
 */
function parseCents(cell: string | undefined, decimal: ',' | '.'): bigint | null {
  const bare = (cell ?? '').replace(/R\$/gi, '').replace(/\s+/g, '')
  const negative = bare.startsWith('-')
  const unsigned = bare.replace(/^[-+]/, '')
  const thousands = decimal === ',' ? '.' : ','
  const plain = unsigned.split(thousands).join('')
  const match = (decimal === ',' ? /^(\d+)(?:,(\d{0,2}))?$/ : /^(\d+)(?:\.(\d{0,2}))?$/).exec(plain)
  if (!match) return null
  const cents = BigInt(match[1] ?? '0') * 100n + BigInt(((match[2] ?? '') + '00').slice(0, 2))
  return negative ? -cents : cents
}

function tokens(cell: string | undefined): string[] {
  return normalizeDescription(cell ?? '')
    .split(' ')
    .filter((t) => t !== '')
}

/** Columns whose header names one of `names`; a column that also says `saldo` is never one. */
function named(header: string[] | undefined, names: string[]): number[] {
  const found: number[] = []
  header?.forEach((cell, column) => {
    const words = tokens(cell)
    if (!words.includes('saldo') && words.some((w) => names.includes(w))) found.push(column)
  })
  return found
}

function modalLength(rows: string[][]): number {
  const votes = new Map<number, number>()
  for (const row of rows) votes.set(row.length, (votes.get(row.length) ?? 0) + 1)
  let best = 0
  let bestVotes = 0
  for (const [length, count] of votes) {
    if (count > bestVotes || (count === bestVotes && length > best)) {
      best = length
      bestVotes = count
    }
  }
  return best
}

/** The columns where 90% of the rows, counted from the first date, are dates, with the row before that first date. */
function findDates(rows: string[][], width: number) {
  const indexes = rows.flatMap((row, i) => (row.length === width ? [i] : []))
  const found: { column: number; headerRow: number }[] = []
  for (let column = 0; column < width; column++) {
    const first = indexes.findIndex((i) => parseDate(rows[i]?.[column]) !== null)
    if (first < 0) continue
    const data = indexes.slice(first)
    const dated = data.filter((i) => parseDate(rows[i]?.[column]) !== null).length
    if (dated / data.length >= MIN_DATE_SHARE) {
      found.push({ column, headerRow: first > 0 ? (indexes[first - 1] ?? -1) : -1 })
    }
  }
  return found
}

/** Comma when any cell of these columns looks like 12,34; the same rule for detection and for the column screen. */
export function detectDecimal(dataRows: string[][], columns: number[]): ',' | '.' {
  const found = columns.some((c) => dataRows.some((r) => /\d,\d{2}$/.test((r[c] ?? '').trim())))
  return found ? ',' : '.'
}

export function detectCsv(parsed: { separator: CsvSeparator; rows: string[][] }): CsvDetection {
  const { separator, rows } = parsed
  const width = modalLength(rows)
  const dates = findDates(rows, width)
  const dateHit = dates[0]
  const headerRow = dateHit?.headerRow ?? -1
  const header = headerRow >= 0 ? rows[headerRow] : undefined
  const data = rows.slice(headerRow + 1).filter((r) => r.length === width)
  const dateColumn = dateHit?.column ?? 0
  const columns = Array.from({ length: width }, (_, c) => c)

  const signed = named(header, SIGNED_NAMES).filter((c) => c !== dateColumn)
  const debit = named(header, DEBIT_NAMES)
  const credit = named(header, CREDIT_NAMES)
  const numeric = columns.filter((c) => {
    if (c === dateColumn || tokens(header?.[c]).includes('saldo')) return false
    const cells = data.map((r) => (r[c] ?? '').trim()).filter((cell) => cell !== '')
    return cells.length > 0 && cells.every((cell) => NUMERIC.test(cell))
  })

  let amount: CsvMapping['amount']
  let amountSure: boolean
  if (signed.length > 0) {
    amount = { kind: 'signed', column: signed[0] ?? 2 }
    amountSure = signed.length === 1 && debit.length + credit.length === 0
  } else if (debit.length + credit.length > 0) {
    amount = { kind: 'split', debit: debit[0] ?? -1, credit: credit[0] ?? -1 }
    amountSure = debit.length === 1 && credit.length === 1
  } else {
    amount = { kind: 'signed', column: numeric[0] ?? 2 }
    amountSure = numeric.length === 1
  }

  const amountColumns = amount.kind === 'signed' ? [amount.column] : [amount.debit, amount.credit]
  const decimal = detectDecimal(data, amountColumns)

  let description: number
  let descriptionSure: boolean
  const byName = named(header, DESCRIPTION_NAMES).filter((c) => c !== dateColumn)
  if (byName.length > 0) {
    description = byName[0] ?? 1
    descriptionSure = byName.length === 1
  } else {
    const texts = columns
      .filter((c) => c !== dateColumn && !amountColumns.includes(c) && !numeric.includes(c))
      .map((c) => ({ c, distinct: new Set(data.map((r) => (r[c] ?? '').trim())).size }))
      .sort((a, b) => b.distinct - a.distinct || a.c - b.c)
    description = texts[0]?.c ?? 1
    descriptionSure = texts.length > 0 && (texts[0]?.distinct ?? 0) > (texts[1]?.distinct ?? 0)
  }

  const certain = dates.length === 1 && amountSure && descriptionSure && width >= 3
  const signature = header
    ? `${header.map((cell) => normalizeDescription(cell)).join('|')}|${separator}`
    : `#cols:${width}|${separator}`
  return {
    mapping: { headerRow, date: dateColumn, description, amount, decimal },
    certain,
    signature,
  }
}

/** The statement lines the mapping reads. Rows whose date or amount cannot be read (titles, balances, footers) are skipped. */
export function linesFromCsv(rows: string[][], mapping: CsvMapping): StatementLine[] {
  const lines: StatementLine[] = []
  for (const row of rows.slice(mapping.headerRow + 1)) {
    const occurredOn = parseDate(row[mapping.date])
    if (occurredOn === null) continue
    let cents: bigint
    if (mapping.amount.kind === 'signed') {
      const value = parseCents(row[mapping.amount.column], mapping.decimal)
      if (value === null) continue
      cents = value
    } else {
      const out = (row[mapping.amount.debit] ?? '').trim()
      const into = (row[mapping.amount.credit] ?? '').trim()
      if (out === '' && into === '') continue
      const debit = out === '' ? 0n : parseCents(out, mapping.decimal)
      const credit = into === '' ? 0n : parseCents(into, mapping.decimal)
      if (debit === null || credit === null) continue
      cents = (credit < 0n ? -credit : credit) - (debit < 0n ? -debit : debit)
    }
    lines.push({
      occurredOn,
      cents,
      description: (row[mapping.description] ?? '').replace(/\s+/g, ' ').trim(),
    })
  }
  if (lines.length > MAX_STATEMENT_LINES) throw new StatementReadError('tooLarge')
  if (lines.length === 0) throw new StatementReadError('empty')
  return lines
}
