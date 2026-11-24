import { describe, expect, it } from 'vitest'
import { listSlot } from '@/shared/modules'
import './register'

describe('planning registrations', () => {
  it('puts the four home cards in order for adults and viewers only', () => {
    const cards = listSlot('home')
    expect(cards.map((card) => card.key)).toEqual([
      'planning-budget-card',
      'planning-reserve-card',
      'planning-projection-card',
      'planning-spending-split-card',
    ])
    for (const card of cards) expect(card.roles).toEqual(['OWNER', 'ADULT', 'VIEWER'])
  })
})
