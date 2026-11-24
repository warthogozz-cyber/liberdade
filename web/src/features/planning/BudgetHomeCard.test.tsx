import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import BudgetHomeCard from './BudgetHomeCard'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-12-05T15:00:00Z')
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

/** November had eight limits of R$ 100,00; the first two categories spent R$ 150,00. */
function setUp(role: 'OWNER' | 'VIEWER' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { planning: true }
  const expense = mockState.categories.filter((each) => each.kind === 'EXPENSE').slice(0, 8)
  for (const [index, each] of expense.entries()) {
    mockState.budgets.push({
      month: '2026-11',
      categoryId: each.id,
      limitCents: 10_000n,
      version: 0,
    })
    if (index < 2) {
      mockState.transactions.push({
        id: crypto.randomUUID(),
        kind: 'EXPENSE',
        amount: { amount: '150.00', currency: 'BRL' },
        occurredOn: '2026-11-03',
        accountId: mockState.accounts[0].id,
        toAccountId: null,
        categoryId: each.id,
        description: null,
        paymentMethod: null,
        tags: [],
        status: 'CONFIRMED',
        scheduledOn: null,
        recurringRuleId: null,
        createdAt: new Date().toISOString(),
        memberId: workspace.membershipId,
        version: 0,
      })
    }
  }
  return workspace
}

const render = () => renderWithProviders(<BudgetHomeCard />, { path: '/' })

describe('BudgetHomeCard', () => {
  it('shows previous month summary and copies', async () => {
    const workspace = setUp()
    render()

    expect(
      await screen.findByText('Novembro: 6 de 8 categorias dentro do limite'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Copiar limites' }))

    await waitFor(() =>
      expect(screen.queryByText(/categorias dentro do limite/)).not.toBeInTheDocument(),
    )
    expect(mockState.budgets.filter((each) => each.month === '2026-12')).toHaveLength(8)
    expect(localStorage.getItem(`liberdade.budget-summary:${workspace.id}:2026-12`)).not.toBeNull()
  })

  it('viewer sees summary without copy', async () => {
    setUp('VIEWER')
    render()

    expect(
      await screen.findByText('Novembro: 6 de 8 categorias dentro do limite'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Copiar limites' })).not.toBeInTheDocument()
  })

  it('not now hides the summary for good', async () => {
    const workspace = setUp()
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Agora não' }))

    expect(screen.queryByText(/categorias dentro do limite/)).not.toBeInTheDocument()
    expect(localStorage.getItem(`liberdade.budget-summary:${workspace.id}:2026-12`)).not.toBeNull()
  })

  it('lists the three most used limits with a link', async () => {
    const workspace = setUp()
    localStorage.setItem(`liberdade.budget-summary:${workspace.id}:2026-12`, '1')
    mockState.budgets.push(...mockState.budgets.map((each) => ({ ...each, month: '2026-12' })))
    render()

    expect(await screen.findByRole('link', { name: 'Ver orçamento' })).toHaveAttribute(
      'href',
      '/planejar/orcamento',
    )
    expect(screen.getAllByRole('meter')).toHaveLength(3)
  })
})
