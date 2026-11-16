import { describe, expect, it } from 'vitest'
import { messages } from '@/shared/i18n'
import { ACCOUNT_TYPES, accountTypeInfo } from './accountTypes'
import { accountsMessages } from './messages.pt-BR'

describe('ACCOUNT_TYPES', () => {
  it('lists the six types in the order they are offered', () => {
    expect(ACCOUNT_TYPES.map((info) => info.type)).toEqual([
      'CHECKING',
      'SAVINGS',
      'CASH',
      'CREDIT_CARD',
      'WALLET',
      'INVESTMENT',
    ])
  })

  it('names every type and hint in the accounts messages, without clashing with the base ones', () => {
    for (const info of ACCOUNT_TYPES) {
      expect(accountsMessages).toHaveProperty([info.labelId])
      expect(accountsMessages).toHaveProperty([info.hintId])
    }
    expect(Object.keys(accountsMessages).filter((id) => id in messages)).toEqual([])
  })

  it('finds the info of a type', () => {
    expect(accountTypeInfo('CREDIT_CARD').icon).toBe('💳')
  })
})
