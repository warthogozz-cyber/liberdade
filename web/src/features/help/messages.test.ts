import { describe, expect, it } from 'vitest'
import { glossary, moduleHelp } from '@/shared/help'
import { LOSS } from '@/shared/help/testing/copyRules'
import { howItWorksMessages } from './messages.pt-BR'

describe('how it works messages', () => {
  it('fit280', () => {
    for (const [id, text] of Object.entries(howItWorksMessages)) {
      expect(text.length, id).toBeLessThanOrEqual(280)
      expect(text, id).not.toMatch(LOSS)
    }
  })

  it('everyCatalogIdExists', () => {
    expect(glossary).toHaveLength(7)
    expect(moduleHelp.map((entry) => entry.module)).toEqual([
      'planning',
      'indicators',
      'gamification',
      'markets',
      'ventures',
    ])
    for (const { termId, bodyId } of glossary) {
      expect(howItWorksMessages).toHaveProperty([termId])
      expect(howItWorksMessages).toHaveProperty([bodyId])
    }
    for (const { module, titleId } of moduleHelp) {
      expect(howItWorksMessages).toHaveProperty([titleId])
      expect(howItWorksMessages).toHaveProperty([`help.module.${module}`])
    }
  })
})
