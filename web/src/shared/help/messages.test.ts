import { describe, expect, it } from 'vitest'
import { helpMessages } from './messages.pt-BR'
import { countSentences, LOSS } from './testing/copyRules'

const EXEMPT = ['tips.dismiss', 'tips.tour.step']

describe('help messages', () => {
  it('tipsFit140AndTwoSentences', () => {
    for (const [id, text] of Object.entries(helpMessages)) {
      expect(text.length, id).toBeLessThanOrEqual(140)
      if (!EXEMPT.includes(id)) expect(countSentences(text), id).toBeLessThanOrEqual(2)
    }
  })

  it('noLossPhrase', () => {
    for (const [id, text] of Object.entries(helpMessages)) {
      expect(text, id).not.toMatch(LOSS)
    }
  })
})
