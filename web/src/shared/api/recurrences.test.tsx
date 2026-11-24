import { waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { onLocalAction, type LocalAction } from '@/shared/events'
import { plannedView, renderHookWithProviders } from '@/shared/testing'
import { useConfirmPlanned } from './recurrences'

describe('useConfirmPlanned', () => {
  it('tells the listeners the confirmed expense, with its category and month', async () => {
    const workspace = seedMockFamily()
    const mercado = mockState.categories.find((each) => each.name === 'Mercado')
    if (!mercado) throw new Error('seed')
    const planned = plannedView(
      {
        id: '0192f3a4-0000-7000-8000-0000000000d2',
        kind: 'EXPENSE',
        amount: { amount: '150.00', currency: 'BRL' },
        occurredOn: '2026-11-20',
        accountId: mockState.accounts[0].id,
        categoryId: mercado.id,
      },
      '2026-11-20',
      { memberId: workspace.membershipId, version: 0 },
    )
    mockState.transactions.push(planned)
    const actions: LocalAction[] = []
    const off = onLocalAction((action) => actions.push(action))
    const hook = renderHookWithProviders(() => useConfirmPlanned(workspace.id))

    hook.result.current.mutate({ id: planned.id, body: { version: planned.version } })

    await waitFor(() => expect(actions).toHaveLength(1))
    expect(actions[0]).toMatchObject({
      type: 'transaction-saved',
      workspaceId: workspace.id,
      categoryId: mercado.id,
      fromQueue: false,
    })
    expect(actions[0].month).toMatch(/^\d{4}-\d{2}$/)
    off()
  })
})
