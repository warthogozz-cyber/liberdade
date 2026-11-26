/**
 * Reads and cleans what a person gives the Feira for an NFC-e: the QR's link, or the typed key
 * (plan 10.2, D15). It is pure and runs on the device. The buyer's document (v1 `cDest`, v3
 * `idDest`) is read past and never leaves this file: not in the result, not in the body the API gets.
 */

export type NfceVersion = 0 | 1 | 2 | 3

export interface ParsedNfce {
  chave: string
  /** 1, 2 or 3 for a QR of that version; 0 for a key that was typed or pasted. */
  version: NfceVersion
  /** What the API validates for versions 2 and 3; never present for 0 and 1. */
  p?: string
  /** `YYYY-MM-DD`, when the QR says it. */
  issuedOn?: string
  /** The QR's own link, only `http(s)`, and only when it carries no buyer document. In memory only. */
  sefazUrl?: string
}

export type NfceInputError = {
  error: 'not-nfce' | 'check-digit' | 'model' | 'homologation' | 'format'
}

/** The IBGE codes of the 27 federative units. */
const UF_CODES = new Set([
  '11', '12', '13', '14', '15', '16', '17', '21', '22', '23', '24', '25', '26', '27', '28', '29',
  '31', '32', '33', '35', '41', '42', '43', '50', '51', '52', '53',
]) // prettier-ignore

const CNPJ_FIRST = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
const CNPJ_SECOND = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]

function mod11Digit(text: string, weights: number[]): number {
  const sum = weights.reduce(
    (total, weight, index) => total + (text.charCodeAt(index) - 48) * weight,
    0,
  )
  const rest = sum % 11
  return rest < 2 ? 0 : 11 - rest
}

/** The alphanumeric-ready CNPJ rule (D4): 12 of `[0-9A-Z]`, 2 digits, weighted modulo 11. */
function isValidCnpj(cnpj: string): boolean {
  if (!/^[0-9A-Z]{12}[0-9]{2}$/.test(cnpj)) return false
  if (new Set(cnpj).size === 1) return false
  const first = mod11Digit(cnpj.slice(0, 12), CNPJ_FIRST)
  if (cnpj.charCodeAt(12) - 48 !== first) return false
  return cnpj.charCodeAt(13) - 48 === mod11Digit(cnpj.slice(0, 13), CNPJ_SECOND)
}

/** The key's own digit: weights 2 to 9 from the right, repeating, over the first 43 positions. */
function keyCheckDigit(body: string): number {
  const sum = [...body]
    .reverse()
    .reduce((total, char, index) => total + (char.charCodeAt(0) - 48) * ((index % 8) + 2), 0)
  const rest = sum % 11
  return rest < 2 ? 0 : 11 - rest
}

/** The same checks as the API's `NfceKey.parse` (D2), in the same order, so a typo shows up here first. */
function checkKey(chave: string, today: Date): NfceInputError['error'] | null {
  // Letters only where the CNPJ is (positions 7 to 18), as the API's key shape allows.
  if (!/^[0-9]{6}[0-9A-Z]{12}[0-9]{26}$/.test(chave)) return 'format'
  if (!UF_CODES.has(chave.slice(0, 2))) return 'format'
  const year = 2000 + Number(chave.slice(2, 4))
  const month = Number(chave.slice(4, 6))
  if (!/^\d{4}$/.test(chave.slice(2, 6)) || month < 1 || month > 12 || year < 2010) return 'format'
  if (year * 12 + month > today.getFullYear() * 12 + today.getMonth() + 1) return 'format'
  if (!isValidCnpj(chave.slice(6, 20))) return 'format'
  if (chave.slice(20, 22) !== '65') return 'model'
  if (chave.charCodeAt(43) - 48 !== keyCheckDigit(chave.slice(0, 43))) return 'check-digit'
  return null
}

const pad = (value: number) => String(value).padStart(2, '0')

/** `YYYY-MM-DD` when the three parts make a real calendar date. */
function isoDate(year: number, month: number, day: number): string | undefined {
  const date = new Date(Date.UTC(year, month - 1, day))
  const real =
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  return real ? `${year}-${pad(month)}-${pad(day)}` : undefined
}

/** The offline QR gives only the day of the month; the key gives the year and the month. */
function issuedOnFromDay(chave: string, day: string): string | undefined {
  if (!/^\d{2}$/.test(day)) return undefined
  return isoDate(2000 + Number(chave.slice(2, 4)), Number(chave.slice(4, 6)), Number(day))
}

/** v1's `dhEmi` is the ISO timestamp, written as hexadecimal text. */
function issuedOnFromHex(dhEmi: string | null): string | undefined {
  if (dhEmi === null || !/^([0-9a-fA-F]{2})+$/.test(dhEmi)) return undefined
  const text = (dhEmi.match(/../g) ?? [])
    .map((pair) => String.fromCharCode(parseInt(pair, 16)))
    .join('')
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(text)
  return match ? isoDate(Number(match[1]), Number(match[2]), Number(match[3])) : undefined
}

/**
 * The link rebuilt from its parts behind a fixed `http(s)://`, so nothing in the text can choose
 * the scheme: no `javascript:`, no user and password, no fragment.
 */
function httpOnly(link: URL): string | undefined {
  if (link.protocol === 'https:') return `https://${link.host}${link.pathname}${link.search}`
  if (link.protocol === 'http:') return `http://${link.host}${link.pathname}${link.search}`
  return undefined
}

function parseLink(link: URL, today: Date): ParsedNfce | NfceInputError {
  const finish = (
    parsed: ParsedNfce,
    tpAmb: string | null | undefined,
    withBuyerDocument: boolean,
  ): ParsedNfce | NfceInputError => {
    const error = checkKey(parsed.chave, today)
    if (error) return { error }
    // Only 1 (production) and 2 (homologation) exist: a missing or odd value is not an NFC-e link.
    if (tpAmb === '2') return { error: 'homologation' }
    if (tpAmb !== '1') return { error: 'not-nfce' }
    const sefazUrl = withBuyerDocument ? undefined : httpOnly(link)
    return sefazUrl === undefined ? parsed : { ...parsed, sefazUrl }
  }

  const params = link.searchParams

  const chNFe = params.get('chNFe')
  if (chNFe !== null) {
    const chave = chNFe.trim().toUpperCase()
    const issuedOn = issuedOnFromHex(params.get('dhEmi'))
    const parsed: ParsedNfce = { chave, version: 1, ...(issuedOn && { issuedOn }) }
    return finish(parsed, params.get('tpAmb'), (params.get('cDest') ?? '') !== '')
  }

  const p = params.get('p')
  if (p === null) return { error: 'not-nfce' }
  const fields = p.split('|')
  const chave = (fields[0] ?? '').trim().toUpperCase()
  const offline = fields.length === 8
  if (fields[1] === '2' && (fields.length === 5 || offline)) {
    const issuedOn = offline ? issuedOnFromDay(chave, fields[3] ?? '') : undefined
    const parsed: ParsedNfce = { chave, version: 2, p, ...(issuedOn && { issuedOn }) }
    return finish(parsed, fields[2], false)
  }
  if (fields[1] === '3' && (fields.length === 3 || offline)) {
    const issuedOn = offline ? issuedOnFromDay(chave, fields[3] ?? '') : undefined
    // Only the key and the environment go on: the offline form's buyer document stays here.
    const parsed: ParsedNfce = {
      chave,
      version: 3,
      p: `${chave}|3|1`,
      ...(issuedOn && { issuedOn }),
    }
    return finish(parsed, fields[2], offline)
  }
  return { error: 'not-nfce' }
}

/** The QR's link, or the 44 positions typed or pasted (spaces and dots are ignored). */
export function parseNfceInput(raw: string, today: Date): ParsedNfce | NfceInputError {
  const text = raw.trim()
  const typed = text.replace(/[\s.]/g, '')
  // Lowercase is normalised like the link paths do; a word with no digit is not a key at all.
  if (/^[0-9A-Za-z]+$/.test(typed) && /[0-9]/.test(typed)) {
    const chave = typed.toUpperCase()
    if (chave.length !== 44) return { error: 'format' }
    const error = checkKey(chave, today)
    return error ? { error } : { chave, version: 0 }
  }
  let link: URL
  try {
    link = new URL(text)
  } catch {
    return { error: 'not-nfce' }
  }
  return parseLink(link, today)
}

/** The body of `lookup` and of the save: the only way to build it, so nothing else goes along. */
export function lookupBody(parsed: ParsedNfce): {
  chave: string
  version: NfceVersion
  p?: string
} {
  return {
    chave: parsed.chave,
    version: parsed.version,
    ...(parsed.p !== undefined && { p: parsed.p }),
  }
}

/** The QR's date; else today, when today is in the key's month; else nothing, and the field is required. */
export function defaultIssuedOn(parsed: ParsedNfce, today: Date): string | undefined {
  if (parsed.issuedOn !== undefined) return parsed.issuedOn
  const year = 2000 + Number(parsed.chave.slice(2, 4))
  const month = Number(parsed.chave.slice(4, 6))
  if (today.getFullYear() !== year || today.getMonth() + 1 !== month) return undefined
  return `${year}-${pad(month)}-${pad(today.getDate())}`
}
