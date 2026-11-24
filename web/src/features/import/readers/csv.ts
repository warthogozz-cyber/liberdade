export type CsvSeparator = ';' | ',' | '\t'

const SEPARATORS: CsvSeparator[] = [';', ',', '\t']
const SAMPLE_LINES = 20
const MIN_COLUMNS = 3

function split(text: string, separator: CsvSeparator): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false
  let touched = false
  const endRow = () => {
    row.push(field)
    if (row.some((cell) => cell.trim() !== '')) rows.push(row)
    row = []
    field = ''
    touched = false
  }
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"'
          i++
        } else quoted = false
      } else field += c
    } else if (c === '"') {
      quoted = true
      touched = true
    } else if (c === separator) {
      row.push(field)
      field = ''
      touched = true
    } else if (c === '\r' || c === '\n') {
      if (c === '\r' && text[i + 1] === '\n') i++
      endRow()
    } else {
      field += c
      touched = true
    }
  }
  if (touched || field !== '') endRow()
  return rows
}

/** Separator whose column count (at least 3) repeats on most of the first non-empty lines. */
function findSeparator(text: string): CsvSeparator {
  let best: CsvSeparator = ';'
  let bestVotes = 0
  for (const separator of SEPARATORS) {
    const sample = split(text, separator).slice(0, SAMPLE_LINES)
    const votes = new Map<number, number>()
    for (const row of sample) votes.set(row.length, (votes.get(row.length) ?? 0) + 1)
    for (const [columns, count] of votes) {
      if (columns >= MIN_COLUMNS && count * 2 > sample.length && count > bestVotes) {
        best = separator
        bestVotes = count
      }
    }
  }
  return best
}

/** Parses CSV text (quotes with "" escapes) after sniffing the separator; empty lines are dropped. */
export function parseCsv(text: string): { separator: CsvSeparator; rows: string[][] } {
  const separator = findSeparator(text)
  return { separator, rows: split(text, separator) }
}
