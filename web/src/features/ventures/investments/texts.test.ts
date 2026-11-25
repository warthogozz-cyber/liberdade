import { describe, expect, it } from 'vitest'
import { INVESTMENT_TOPICS, REVIEWED_ON } from './texts'

const ALLOWED = new Set([
  'FGC',
  'CDB',
  'LCI',
  'LCA',
  'CDI',
  'Selic',
  'IPCA',
  'Tesouro',
  'Direto',
  'Banco',
  'Central',
  'CVM',
  'B3',
  'Imposto',
  'Renda',
  'IR',
])

/** Every string of every topic, as its own piece of text. */
const texts = INVESTMENT_TOPICS.flatMap((topic) => [
  topic.name,
  topic.whatItIs,
  topic.risk,
  topic.liquidity,
  topic.guarantee,
  topic.tax,
])

describe('investment texts', () => {
  it('fixedOrder', () => {
    expect(INVESTMENT_TOPICS.map((topic) => topic.id)).toEqual([
      'poupanca',
      'renda-fixa',
      'tesouro-direto',
      'fundos',
      'acoes',
    ])
  })

  it('noRecommendationWords', () => {
    const forbidden = [/recomend/i, /melhor/i, /ideal para você/i, /indicamos/i, /garantido/i]
    for (const text of texts) {
      for (const word of forbidden) expect(text).not.toMatch(word)
    }
  })

  it('noProperNamesOutsideTheAllowList', () => {
    for (const text of texts) {
      // "R$" is a currency sign, not a name.
      const clean = text.replace(/R\$/g, '')
      for (const match of clean.matchAll(/\p{Lu}[\p{L}\p{N}]*/gu)) {
        const before = clean.slice(0, match.index).trimEnd()
        const opensSentence = before === '' || /[.!?]$/.test(before)
        if (!opensSentence) expect(ALLOWED, `"${match[0]}" in: ${text}`).toContain(match[0])
      }
    }
  })

  it('fgcCitesItsDate', () => {
    const [day, month, year] = REVIEWED_ON.split('-').reverse()
    for (const id of ['poupanca', 'renda-fixa']) {
      const guarantee = INVESTMENT_TOPICS.find((topic) => topic.id === id)?.guarantee ?? ''
      expect(guarantee).toContain('FGC')
      expect(guarantee).toMatch(/R\$ 250 mil/)
      expect(guarantee).toContain(`${day}/${month}/${year}`)
    }
  })
})
