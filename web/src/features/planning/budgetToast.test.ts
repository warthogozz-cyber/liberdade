import { describe, expect, it } from 'vitest'
import type { BudgetLineView } from '@/shared/api/planning'
import { budgetToastFor } from './budgetToast'

function line(
  categoryId: string,
  state: BudgetLineView['state'],
  spent: string,
  limit = '1000.00',
): BudgetLineView {
  const spentCents = Math.round(Number(spent) * 100)
  const limitCents = Math.round(Number(limit) * 100)
  return {
    categoryId,
    limit: { amount: limit, currency: 'BRL' },
    spent: { amount: spent, currency: 'BRL' },
    usagePercent: Math.floor((spentCents * 100) / limitCents),
    state,
    overBy:
      state === 'OVER'
        ? { amount: (spentCents / 100 - limitCents / 100).toFixed(2), currency: 'BRL' }
        : null,
    version: 0,
  }
}

const none = () => false

describe('budgetToastFor', () => {
  it('first crossing of 80', () => {
    const result = budgetToastFor([line('cat', 'NEAR', '800.00')], 'cat', null, none)

    expect(result).toEqual({ message: 'near', categoryId: 'cat', keys: ['cat:80'] })
  })

  it('jumping past 100 marks both', () => {
    const result = budgetToastFor([line('cat', 'OVER', '1060.00')], 'cat', null, none)

    expect(result).toEqual({
      message: 'over',
      categoryId: 'cat',
      overBy: { amount: '60.00', currency: 'BRL' },
      keys: ['cat:100', 'cat:80'],
    })
  })

  it('already seen is silent', () => {
    const seen = (key: string) => key === 'cat:80'

    expect(budgetToastFor([line('cat', 'NEAR', '800.00')], 'cat', null, seen)).toBeNull()
  })

  it('reaching the limit after the 80 notice still warns once', () => {
    const seen = (key: string) => key === 'cat:80'

    expect(budgetToastFor([line('cat', 'AT', '1000.00')], 'cat', null, seen)).toEqual({
      message: 'at',
      categoryId: 'cat',
      keys: ['cat:100', 'cat:80'],
    })
    expect(
      budgetToastFor([line('cat', 'AT', '1000.00')], 'cat', null, (key) => key.endsWith(':100')),
    ).toBeNull()
  })

  it('under 80 is silent', () => {
    expect(budgetToastFor([line('cat', 'UNDER', '100.00')], 'cat', null, none)).toBeNull()
  })

  it('parent line counts', () => {
    const lines = [line('parent', 'AT', '1000.00')]

    expect(budgetToastFor(lines, 'child', 'parent', none)).toEqual({
      message: 'at',
      categoryId: 'parent',
      keys: ['parent:100', 'parent:80'],
    })
  })
})
