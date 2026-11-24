import { normalizeDescription } from './normalize'
import type { CsvSeparator } from './readers/csv'
import { calendarDate } from './readers/dates'
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

/**
 * dd/MM/yyyy or dd/MM/yy, optionally followed by a time, or yyyy-MM-dd, optionally followed by a time (and zone).
 * Only the date part is kept.
 */
const DATE =
  /^(\d{2})\/(\d{2})\/(\d{4}|\d{2})(?:\s+\d{2}:\d{2}(?::\d{2})?)?$|^(\d{4})-(\d{2})-(\d{2})(?:[T\s]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?)?$/i
const MIN_DATE_SHARE = 0.9
const SIGNED_NAMES = ['valor', 'amount', 'value']
const DEBIT_NAMES = ['debito', 'saida']
const CREDIT_NAMES = ['credito', 'entrada']
const DESCRIPTION_NAMES = ['descricao', 'historico', 'lancamento', 'memo', 'description']
const NUMERIC = /^[-+]?\s*(?:R\$)?\s*[-+]?[\d.,\s]*\d[\d.,\s]*$/i

/**
 * The cell as yyyy-MM-dd, or null when it is not a real calendar date in one of the accepted layouts. A two-digit
 * year is 20yy.
 */
function parseDate(cell: string | undefined): string | null {
  const match = DATE.exec((cell ?? '').trim())
  if (!match) return null
  if (match[1]) {
    const year = match[3]?.length === 2 ? `20${match[3]}` : (match[3] ?? '')
    return calendarDate(year, match[2] ?? '', match[1])
  }
  return calendarDate(match[4] ?? '', match[5] ?? '', match[6] ?? '')
}

/** The cell without currency symbol, spaces and sign, and whether it was negative. */
function unsignedDigits(cell: string | undefined): { negative: boolean; digits: string } {
  const bare = (cell ?? '').replace(/R\$/gi, '').replace(/\s+/g, '')
  return { negative: bare.startsWith('-'), digits: bare.replace(/^[-+]/, '') }
}

const COMMA_DECIMAL = /^(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{0,2}))?$/
const DOT_DECIMAL = /^(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{0,2}))?$/

/**
 * Cents of an amount cell, or null when it is not an amount (the row is then skipped). The thousands character is
 * the one that is not the decimal and counts only before groups of exactly three digits; at most two fraction
 * digits are accepted, padded. Anything else (1.23 in a comma file, 12,345 as a fraction) is refused, never guessed.
 */
function parseCents(cell: string | undefined, decimal: ',' | '.'): bigint | null {
  const { negative, digits } = unsignedDigits(cell)
  const match = (decimal === ',' ? COMMA_DECIMAL : DOT_DECIMAL).exec(digits)
  if (!match) return null
  const whole = (match[1] ?? '0').replace(/[.,]/g, '')
  const cents = BigInt(whole) * 100n + BigInt(((match[2] ?? '') + '00').slice(0, 2))
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

/**
 * What one amount cell says about the decimal character. Sure: the last separator is followed by one or two digits
 * (45,9 or 1.234,56), or the same separator splits several groups of three (1.234.567), which makes it the
 * thousands one. Leaning: a single separator before exactly three digits (1.234), most likely thousands but
 * possibly a fraction. Null: no separator, or no number.
 */
function decimalVote(cell: string | undefined): { decimal: ',' | '.'; sure: boolean } | null {
  const { digits } = unsignedDigits(cell)
  if (!/^\d[\d.,]*$/.test(digits)) return null
  const last = Math.max(digits.lastIndexOf(','), digits.lastIndexOf('.'))
  if (last < 0) return null
  const separator = digits[last] === ',' ? ',' : '.'
  const other = separator === ',' ? '.' : ','
  const tail = digits.slice(last + 1)
  const count = digits.split(separator).length - 1
  if (/^\d{1,2}$/.test(tail) && (count === 1 || digits.includes(other))) {
    return { decimal: separator, sure: true }
  }
  if (/^\d{3}$/.test(tail) && !digits.includes(other)) {
    return { decimal: other, sure: count > 1 }
  }
  return null
}

/**
 * The decimal character of these columns, voted on every cell. It is certain when some cell is sure of it and no
 * cell points the other way; a file of whole numbers is certain too (the character changes no value). With only
 * leaning cells (1.234) or conflicting ones, the likelier character comes back uncertain, so the person confirms
 * it; without any evidence the Brazilian comma is the default.
 */
export function decimalEvidence(
  dataRows: string[][],
  columns: number[],
): { decimal: ',' | '.'; certain: boolean } {
  const sure = { ',': 0, '.': 0 }
  const all = { ',': 0, '.': 0 }
  for (const row of dataRows) {
    for (const column of columns) {
      const vote = decimalVote(row[column])
      if (!vote) continue
      all[vote.decimal]++
      if (vote.sure) sure[vote.decimal]++
    }
  }
  if (all[','] === 0 && all['.'] === 0) return { decimal: ',', certain: true }
  let decimal: ',' | '.'
  if (sure[','] !== sure['.']) decimal = sure[','] > sure['.'] ? ',' : '.'
  else decimal = all[','] >= all['.'] ? ',' : '.'
  const against = all[decimal === ',' ? '.' : ',']
  return { decimal, certain: sure[decimal] > 0 && against === 0 }
}

/** The likelier decimal character of these columns; the same rule for detection and for the column screen. */
export function detectDecimal(dataRows: string[][], columns: number[]): ',' | '.' {
  return decimalEvidence(dataRows, columns).decimal
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
  const { decimal, certain: decimalSure } = decimalEvidence(data, amountColumns)

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

  const certain = dates.length === 1 && amountSure && descriptionSure && decimalSure && width >= 3
  const signature = header
    ? `${header.map((cell) => normalizeDescription(cell)).join('|')}|${separator}`
    : `#cols:${width}|${separator}`
  return {
    mapping: { headerRow, date: dateColumn, description, amount, decimal },
    certain,
    signature,
  }
}

/** The amount the row's cells give, or null when they give none. */
function rowCents(row: string[], mapping: CsvMapping): bigint | null {
  if (mapping.amount.kind === 'signed')
    return parseCents(row[mapping.amount.column], mapping.decimal)
  const out = (row[mapping.amount.debit] ?? '').trim()
  const into = (row[mapping.amount.credit] ?? '').trim()
  if (out === '' && into === '') return null
  const debit = out === '' ? 0n : parseCents(out, mapping.decimal)
  const credit = into === '' ? 0n : parseCents(into, mapping.decimal)
  if (debit === null || credit === null) return null
  return (credit < 0n ? -credit : credit) - (debit < 0n ? -debit : debit)
}

/**
 * The statement lines the mapping reads, and how many rows after the header were skipped because their date or
 * amount could not be read (titles, footers, or a wrong mapping), so the preview can say so.
 */
export function linesFromCsv(
  rows: string[][],
  mapping: CsvMapping,
): { lines: StatementLine[]; skipped: number } {
  const lines: StatementLine[] = []
  let skipped = 0
  for (const row of rows.slice(mapping.headerRow + 1)) {
    const occurredOn = parseDate(row[mapping.date])
    const cents = occurredOn === null ? null : rowCents(row, mapping)
    if (occurredOn === null || cents === null) {
      skipped++
      continue
    }
    lines.push({
      occurredOn,
      cents,
      description: (row[mapping.description] ?? '').replace(/\s+/g, ' ').trim(),
    })
  }
  if (lines.length > MAX_STATEMENT_LINES) throw new StatementReadError('tooLarge')
  if (lines.length === 0) throw new StatementReadError('empty')
  return { lines, skipped }
}
