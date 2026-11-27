import { describe, expect, it } from 'vitest'
import { screenTips, seenId, tours } from './catalog'
import { helpMessages } from './messages.pt-BR'

const entries = Object.entries(tours)

describe('help catalog', () => {
  it('toursHaveOneToFourSteps', () => {
    for (const [id, steps] of entries) {
      expect(steps.length, id).toBeGreaterThanOrEqual(1)
      expect(steps.length, id).toBeLessThanOrEqual(4)
    }
  })

  it('tourAnchorsAreUnique', () => {
    for (const [id, steps] of entries) {
      const anchors = steps.map((step) => step.anchor)
      expect(new Set(anchors).size, id).toBe(anchors.length)
    }
  })

  it('tourMessagesExist', () => {
    for (const [id, steps] of entries) {
      for (const step of steps) expect(helpMessages, id).toHaveProperty(step.messageId)
    }
  })

  it('everyTipHasText', () => {
    for (const [id, messageId] of Object.entries(screenTips)) {
      expect(helpMessages, id).toHaveProperty(messageId)
    }
    expect(Object.keys(screenTips)).toHaveLength(20)
  })

  it('seenIds', () => {
    expect(seenId.tip('home')).toBe('home')
    expect(seenId.tour('planning')).toBe('tour-planning')
    expect(seenId.event('offline')).toBe('event-offline')
  })
})
