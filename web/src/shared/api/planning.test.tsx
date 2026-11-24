import { waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { mockState, seedMockFamily, seedPlanningFixture } from '@/mocks/data'
import { onLocalAction, type LocalAction } from '@/shared/events'
import { renderHookWithProviders } from '@/shared/testing'
import {
  planningKeys,
  useAddContribution,
  useBudgets,
  useGoals,
  useProjection,
  useReserve,
  useSpendingSplit,
} from './planning'

function setUp() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning: true }
  seedPlanningFixture(workspace)
  return workspace
}

const month = new Date().toISOString().slice(0, 7)

describe('planning hooks', () => {
  it('reads the fixture under the planning keys', async () => {
    const workspace = setUp()
    const budgets = renderHookWithProviders(() => useBudgets(workspace.id, month))
    const goals = renderHookWithProviders(() => useGoals(workspace.id))
    const reserve = renderHookWithProviders(() => useReserve(workspace.id))
    const projection = renderHookWithProviders(() => useProjection(workspace.id))
    const split = renderHookWithProviders(() => useSpendingSplit(workspace.id, month))

    await waitFor(() => expect(budgets.result.current.data?.lines).toHaveLength(1))
    expect(budgets.result.current.data?.lines[0].state).toBe('NEAR')
    await waitFor(() => expect(goals.result.current.data?.goals).toHaveLength(2))
    await waitFor(() => expect(reserve.result.current.data?.costSource).toBe('HISTORY'))
    await waitFor(() => expect(projection.result.current.data?.points).toHaveLength(4))
    expect(projection.result.current.data?.hasHistory).toBe(true)
    await waitFor(() => expect(split.result.current.data?.month).toBe(month))
    expect(
      budgets.queryClient.getQueryState(planningKeys.budgets(workspace.id, month))?.status,
    ).toBe('success')
  })

  it('does not fetch without a family', () => {
    const goals = renderHookWithProviders(() => useGoals(''))

    expect(goals.result.current.fetchStatus).toBe('idle')
  })

  it('a saved contribution refreshes planning and tells the listeners', async () => {
    const workspace = setUp()
    const goalId = mockState.goals[0].id
    const actions: LocalAction[] = []
    const off = onLocalAction((action) => actions.push(action))
    const goals = renderHookWithProviders(() => ({
      goals: useGoals(workspace.id),
      add: useAddContribution(workspace.id),
    }))
    await waitFor(() => expect(goals.result.current.goals.data?.goals).toHaveLength(2))

    goals.result.current.add.mutate({
      id: goalId,
      body: { amount: { amount: '50.00', currency: 'BRL' }, occurredOn: `${month}-10` },
    })

    await waitFor(() =>
      expect(actions).toEqual([
        { type: 'contribution-saved', workspaceId: workspace.id, fromQueue: false },
      ]),
    )
    off()
    await waitFor(() =>
      expect(
        goals.result.current.goals.data?.goals.find((g) => g.id === goalId)?.saved.amount,
      ).toBe('1050.00'),
    )
  })
})
