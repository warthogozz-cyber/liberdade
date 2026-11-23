import { describe, expect, it } from 'vitest'
import type { RecurringRuleView } from '@/shared/api/types'
import { ruleText } from './ruleText'

function rule(overrides: Partial<RecurringRuleView>): RecurringRuleView {
  return {
    id: '0192f3a4-0000-7000-8000-0000000000c1',
    accountId: 'account',
    amount: { amount: '10.00', currency: 'BRL' },
    categoryId: null,
    createdBy: null,
    description: null,
    endOn: null,
    frequency: 'MONTHLY',
    interval: 1,
    kind: 'EXPENSE',
    nextDueOn: null,
    paymentMethod: null,
    startOn: '2026-11-10',
    toAccountId: null,
    version: 0,
    ...overrides,
  }
}

describe('ruleText', () => {
  it('says every month on the day it starts', () => {
    expect(ruleText(rule({ frequency: 'MONTHLY', interval: 1, startOn: '2026-11-10' }))).toBe(
      'Todo mês, dia 10',
    )
  })

  it('says every n weeks on the weekday it starts', () => {
    // 2026-11-16 is a Monday.
    expect(ruleText(rule({ frequency: 'WEEKLY', interval: 2, startOn: '2026-11-16' }))).toBe(
      'A cada 2 semanas, segunda',
    )
  })

  it('says every year on the day and month it starts', () => {
    expect(ruleText(rule({ frequency: 'YEARLY', interval: 1, startOn: '2024-02-29' }))).toBe(
      'Todo ano, 29/02',
    )
  })
})
