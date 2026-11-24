import { calendarDate } from './dates'
import { MAX_STATEMENT_LINES, StatementReadError, type StatementLine } from './types'

const ENTITIES: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
}

/** The character a numeric reference names, or the reference as written when it names none. */
function fromCodePoint(reference: string, code: number): string {
  const valid =
    Number.isInteger(code) && code > 0 && code <= 0x10ffff && (code < 0xd800 || code > 0xdfff)
  return valid ? String.fromCodePoint(code) : reference
}

/** Named entities (`&amp;`) and numeric references, decimal (`&#231;`) or hexadecimal (`&#xE7;`). */
function decodeEntities(text: string): string {
  return text.replace(
    /&(?:(amp|lt|gt|quot|apos)|#(\d{1,7})|#[xX]([0-9a-fA-F]{1,6}));/g,
    (reference, name: string | undefined, decimal: string | undefined, hex: string | undefined) => {
      if (name) return ENTITIES[name] ?? ''
      return fromCodePoint(reference, decimal ? Number(decimal) : parseInt(hex ?? '', 16))
    },
  )
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
  const [, year = '', month = '', day = ''] = match
  const date = calendarDate(year, month, day)
  if (date === null) throw new StatementReadError('unreadable')
  return date
}

function parseAmount(raw: string | undefined): bigint {
  const match = /^([+-])?(\d+)(?:[.,](\d*))?$/.exec((raw ?? '').trim())
  if (!match) throw new StatementReadError('unreadable')
  const fraction = match[3] ?? ''
  // A value finer than a cent (-12.345) cannot be stored; only trailing zeros (-12.3400) may go.
  if (/[1-9]/.test(fraction.slice(2))) throw new StatementReadError('unreadable')
  const cents = BigInt(match[2] ?? '0') * 100n + BigInt((fraction + '00').slice(0, 2))
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
