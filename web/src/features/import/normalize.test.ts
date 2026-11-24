import { describe, expect, it } from 'vitest'
import { normalizeDescription } from './normalize'

// The same table as DescriptionKeyTest in the API: both sides must produce the same key.
const VECTORS: [string, string][] = [
  ['PADARIA SÃO JOÃO', 'padaria sao joao'],
  ['  Uber   *Trip  ', 'uber trip'],
  ['LOJA X PARC 03/10', 'loja x'],
  ['Loja X parcela 3 de 10', 'loja x'],
  ['LOJA X 03/10', 'loja x'],
  ['Pgto. fatura—cartão', 'pgto fatura cartao'],
  ['', ''],
  ['Ação 2025', 'acao 2025'],
]

describe('normalizeDescription', () => {
  it.each(VECTORS)('vectors %j', (input, key) => {
    expect(normalizeDescription(input)).toBe(key)
  })

  it('decomposedAndComposedAccentsAgree', () => {
    expect(normalizeDescription('Ação')).toBe('acao')
    expect(normalizeDescription('Ação')).toBe('acao')
  })

  it('noBreakSpaceIsNotAnInstallmentSpace', () => {
    // Java's \s is ASCII only, so U+00A0 must not let the marker match here either.
    expect(normalizeDescription('LOJA 3 / 10')).toBe('loja 3 10')
    expect(normalizeDescription('LOJA 3 / 10')).toBe('loja')
  })
})
