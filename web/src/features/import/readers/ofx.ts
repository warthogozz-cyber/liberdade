import { MAX_STATEMENT_LINES, StatementReadError, type StatementLine } from './types'

const ENTITIES: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
}

function decodeEntities(text: string): string {
  return text.replace(/&(amp|lt|gt|quot|apos);/g, (_, name: string) => ENTITIES[name] ?? '')
}

/** First value of each tag in a transaction block; works with or without closing tags. */
function fields(block: string): Map<string, string> {
  const found = new Map<string, string>()
  for (const match of block.matchAll(/<(\w+)>([^<\r\n]*)/g)) {
    const name = (match[1] ?? '').toUpperCase()
    if (!found.has(name)) found.set(name, (match[2] ?? '').trim())
  }
  return found
}

function parseDate(raw: string | undefined): string {
  const match = /^(\d{4})(\d{2})(\d{2})/.exec(raw ?? '')
  if (!match) throw new StatementReadError('unreadable')
  const [, year, month, day] = match
  const m = Number(month)
  const d = Number(day)
  if (m < 1 || m > 12 || d < 1 || d > 31) throw new StatementReadError('unreadable')
  return `${year}-${month}-${day}`
}

function parseAmount(raw: string | undefined): bigint {
  const match = /^([+-])?(\d+)(?:[.,](\d*))?$/.exec((raw ?? '').trim())
  if (!match) throw new StatementReadError('unreadable')
  const cents = BigInt(match[2] ?? '0') * 100n + BigInt(((match[3] ?? '') + '00').slice(0, 2))
  return match[1] === '-' ? -cents : cents
}

function describe(f: Map<string, string>): string {
  const name = decodeEntities(f.get('NAME') ?? '')
    .replace(/\s+/g, ' ')
    .trim()
  const memo = decodeEntities(f.get('MEMO') ?? '')
    .replace(/\s+/g, ' ')
    .trim()
  if (name && memo && name !== memo) return `${name} - ${memo}`
  return name || memo
}

/** Reads the transactions of an OFX 1.x (SGML) or 2.x (XML) statement. */
export function readOfx(text: string): StatementLine[] {
  if (!/<OFX[\s>]/i.test(text)) throw new StatementReadError('unreadable')
  const blocks = [
    ...text.matchAll(/<STMTTRN>([\s\S]*?)(?=<\/STMTTRN>|<STMTTRN>|<\/BANKTRANLIST>|$)/gi),
  ]
  if (blocks.length === 0) throw new StatementReadError('empty')
  if (blocks.length > MAX_STATEMENT_LINES) throw new StatementReadError('tooLarge')
  return blocks.map((block) => {
    const f = fields(block[1] ?? '')
    return {
      occurredOn: parseDate(f.get('DTPOSTED')),
      cents: parseAmount(f.get('TRNAMT')),
      description: describe(f),
    }
  })
}
