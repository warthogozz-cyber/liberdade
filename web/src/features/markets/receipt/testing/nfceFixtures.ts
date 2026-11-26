/**
 * Invented NFC-e access keys with computed check digits, mirroring the API's `NfceKeyFixtures`
 * (plan 10.2, D3). The CNPJ base is invented, never a real company.
 */

/** Invented issuer: root `12345678`, branch `0001`. */
export const CNPJ_BASE = '123456780001'

const FIRST = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
const SECOND = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]

function digit(text: string, weights: number[]): number {
  const sum = weights.reduce(
    (total, weight, index) => total + (text.charCodeAt(index) - 48) * weight,
    0,
  )
  const rest = sum % 11
  return rest < 2 ? 0 : 11 - rest
}

/** The 14-position CNPJ for a 12-position base. */
export function cnpjOf(base12: string): string {
  const partial = base12 + digit(base12, FIRST)
  return partial + digit(partial, SECOND)
}

export interface MakeChaveOptions {
  uf?: string
  aamm?: string
  cnpjBase?: string
  serie?: number
  number?: number
  tpEmis?: number
  /** `65` for a consumer receipt; `55` is a company invoice. */
  model?: string
}

/** A valid key: the CNPJ and the key's own check digit are computed. */
export function makeChave(options: MakeChaveOptions = {}): string {
  const {
    uf = '43',
    aamm = '2611',
    cnpjBase = CNPJ_BASE,
    serie = 1,
    number = 1,
    tpEmis = 1,
    model = '65',
  } = options
  const serieText = String(serie).padStart(3, '0')
  const numberText = String(number).padStart(9, '0')
  const body = `${uf}${aamm}${cnpjOf(cnpjBase)}${model}${serieText}${numberText}${tpEmis}12345678`
  const sum = [...body]
    .reverse()
    .reduce((total, char, index) => total + (char.charCodeAt(0) - 48) * ((index % 8) + 2), 0)
  const rest = sum % 11
  return body + (rest < 2 ? 0 : 11 - rest)
}

/** The same key with the last digit changed. */
export function withWrongCheckDigit(chave: string): string {
  return chave.slice(0, -1) + (chave.endsWith('0') ? '1' : '0')
}
