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
  // The full names of the acronyms, spelled out where each first appears.
  'Fundo',
  'Garantidor',
  'Créditos',
  'Certificado',
  'Depósito',
  'Bancário',
  'Letras',
  'Crédito',
  'Imobiliário',
  'Agronegócio',
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
      // "Garantidor" is part of the FGC's own name, not a promise.
      const clean = text.replace(/Fundo Garantidor de Créditos/g, 'FGC')
      for (const word of forbidden) expect(clean).not.toMatch(word)
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

  it('oneSentencePerField', () => {
    for (const topic of INVESTMENT_TOPICS) {
      for (const text of [
        topic.whatItIs,
        topic.risk,
        topic.liquidity,
        topic.guarantee,
        topic.tax,
      ]) {
        expect(text.replace(/[.!?]$/, ''), text).not.toMatch(/[.!?]/)
        expect(text.length, text).toBeLessThanOrEqual(280)
      }
    }
  })

  it('acronymsExplainedOnFirstUse', () => {
    // In card order, the first field that names each acronym carries its explanation.
    const explanations: [string, RegExp][] = [
      ['FGC', /Fundo Garantidor de Créditos/],
      ['CDB', /Certificado de Depósito Bancário/],
      ['LCI', /Letras de Crédito Imobiliário/],
      ['LCA', /do Agronegócio/],
      ['CDI', /taxa que os bancos usam entre si/],
      ['IPCA', /inflação oficial/],
      ['Selic', /taxa básica de juros/],
    ]
    const fields = INVESTMENT_TOPICS.flatMap((topic) => [
      topic.whatItIs,
      topic.risk,
      topic.liquidity,
      topic.guarantee,
      topic.tax,
    ])
    for (const [acronym, explanation] of explanations) {
      const first = fields.find((text) => new RegExp(`\\b${acronym}\\b`).test(text))
      expect(first, acronym).toMatch(explanation)
    }
    const renda = INVESTMENT_TOPICS.find((topic) => topic.id === 'renda-fixa')
    expect(renda?.tax).toContain('alíquota (a porcentagem de imposto)')
    const tesouro = INVESTMENT_TOPICS.find((topic) => topic.id === 'tesouro-direto')
    expect(tesouro?.tax).toContain('custódia (a taxa de guarda dos títulos)')
  })

  it('noLossPhrase', () => {
    const loss = /perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i
    for (const text of texts) expect(text).not.toMatch(loss)
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
