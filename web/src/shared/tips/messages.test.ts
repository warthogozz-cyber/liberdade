import { describe, expect, it } from 'vitest'
import { tipsMessages } from './messages.pt-BR'

// Task 12 adds the tour steps to this same file, so they are covered by these loops.
const LOSS = /perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i

describe('tips messages', () => {
  it('everyTextFitsIn140Characters', () => {
    for (const [id, text] of Object.entries(tipsMessages)) {
      expect(text.length, id).toBeLessThanOrEqual(140)
    }
  })

  it('noLossPhrase', () => {
    for (const [id, text] of Object.entries(tipsMessages)) {
      expect(text, id).not.toMatch(LOSS)
    }
  })
})
