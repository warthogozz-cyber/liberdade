import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import type { AccountView, CategoryView } from '@/shared/api'
import { outboxPayload, renderWithProviders, transactionView } from '@/shared/testing'
import { VersionConflictDialog } from './VersionConflictDialog'

afterEach(cleanup)

describe('VersionConflictDialog', () => {
  it('showsDifferences', () => {
    seedMockFamily()
    const account = mockState.accounts[0]
    const mercado = mockState.categories.find((each) => each.name === 'Mercado')!
    const base = outboxPayload('0192f3a4-0000-7000-8000-0000000000e1', {
      accountId: account.id,
      categoryId: mercado.id,
      occurredOn: '2026-11-15',
    })
    const current = { ...transactionView(base), amount: { amount: '20.00', currency: 'BRL' } }
    const saved = { ...base, version: 0, amount: { amount: '99.00', currency: 'BRL' } }
    renderWithProviders(
      <VersionConflictDialog
        open
        saved={saved}
        current={current}
        categories={new Map(mockState.categories.map((c) => [c.id, c as unknown as CategoryView]))}
        accounts={new Map(mockState.accounts.map((a) => [a.id, a as unknown as AccountView]))}
        onKeepTheirs={vi.fn()}
        onKeepMine={vi.fn()}
      />,
    )

    const dialog = screen.getByRole('alertdialog', { name: 'Este lançamento mudou' })
    expect(within(dialog).getByText('R$ 20,00')).toBeInTheDocument()
    expect(within(dialog).getAllByRole('term')).toHaveLength(1)
    expect(within(dialog).queryByText('15/11/2026')).not.toBeInTheDocument()
  })
})
