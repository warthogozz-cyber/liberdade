import { describe, expect, it } from 'vitest'
import type { BudgetLineView } from '@/shared/api/planning'
import { monthSummary } from './monthSummary'

function line(index: number, spent: string, limit = '100.00'): BudgetLineView {
  return {
    categoryId: `cat-${index}`,
    limit: { amount: limit, currency: 'BRL' },
    spent: { amount: spent, currency: 'BRL' },
    usagePercent: 0,
    state: 'UNDER',
    overBy: null,
    version: 0,
  }
}

describe('monthSummary', () => {
  it('counts within limit', () => {
    const lines = [
      line(0, '150.00'),
      line(1, '100.01'),
      line(2, '100.00'),
      line(3, '99.99'),
      line(4, '0.00'),
      line(5, '50.00'),
      line(6, '80.00'),
      line(7, '10.00'),
    ]

    expect(monthSummary(lines)).toEqual({ within: 6, total: 8 })
  })

  it('has nothing for no lines', () => {
    expect(monthSummary([])).toEqual({ within: 0, total: 0 })
  })
})
