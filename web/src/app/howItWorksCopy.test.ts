import { describe, expect, it } from 'vitest'
import { howItWorksMessages } from '@/features/help/messages.pt-BR'
import { FGC_LIMIT } from '@/features/ventures/investments/texts'

// Here and not next to the help messages: a core feature may not import the projects module, not
// even in a test, and the app layer may import both.
describe('how it works copy', () => {
  it('fgcMatchesInvestments', () => {
    expect(howItWorksMessages['help.def.fgc']).toContain('R$ 250 mil')
    expect(FGC_LIMIT).toContain('R$ 250 mil')
  })
})
