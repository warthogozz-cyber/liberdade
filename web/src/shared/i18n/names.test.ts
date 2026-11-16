import { describe, expect, it } from 'vitest'
import { firstName } from '@/shared/i18n'

describe('firstName', () => {
  it('is the first word of the name', () => {
    expect(firstName('Ana Teste')).toBe('Ana')
  })

  it('ignores the spaces around the name', () => {
    expect(firstName('  Ana  ')).toBe('Ana')
  })

  it('is empty for an empty name', () => {
    expect(firstName('')).toBe('')
  })
})
