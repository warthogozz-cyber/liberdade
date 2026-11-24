import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedPlanningFixture } from '@/mocks/data'
import type { GoalView } from '@/shared/api/planning'
import { onLocalAction, type LocalAction } from '@/shared/events'
import { renderWithProviders } from '@/shared/testing'
import { ContributionSheet } from './ContributionSheet'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function render(direction: 'save' | 'withdraw') {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning: true }
  seedPlanningFixture(workspace)
  const stored = mockState.goals.find((each) => each.name === 'Viagem')
  if (!stored) throw new Error('seed')
  const goal: GoalView = {
    id: stored.id,
    name: stored.name,
    icon: stored.icon,
    targetAmount: { amount: '5000.00', currency: 'BRL' },
    saved: { amount: '1000.00', currency: 'BRL' },
    percent: 20,
    dueOn: stored.dueOn,
    suggestedMonthly: null,
    linked: false,
    linkedAccountId: null,
    achievedAt: null,
    version: 0,
  }
  renderWithProviders(
    <ContributionSheet
      workspace={workspace}
      goal={goal}
      direction={direction}
      onClose={() => {}}
    />,
  )
  return workspace
}

describe('ContributionSheet', () => {
  it('negative balance message', async () => {
    render('withdraw')

    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Valor'), { target: { value: '2.000,00' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Retirar' }))

    expect(await screen.findByText('O guardado não pode ficar negativo.')).toBeInTheDocument()
  })

  it('emits contribution-saved', async () => {
    const actions: LocalAction[] = []
    const off = onLocalAction((action) => actions.push(action))
    const workspace = render('save')

    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Valor'), { target: { value: '200,00' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Guardar' }))

    await waitFor(() =>
      expect(actions).toEqual([
        { type: 'contribution-saved', workspaceId: workspace.id, fromQueue: false },
      ]),
    )
    off()
  })
})
