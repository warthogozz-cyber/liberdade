import { describe, expect, it } from 'vitest'
import { gamificationMessages } from './messages.pt-BR'

const LOSS_WORDING = /perd|perca|resta|expir|quebr|corra|última chance|não deixe|falta/i

describe('gamification messages', () => {
  it('nenhuma frase de perda', () => {
    for (const [id, text] of Object.entries(gamificationMessages)) {
      expect(text, id).not.toMatch(LOSS_WORDING)
    }
  })
})
