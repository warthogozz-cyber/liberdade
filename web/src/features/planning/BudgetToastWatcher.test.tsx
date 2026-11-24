import { cleanup, screen } from '@testing-library/react'
import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedPlanningFixture } from '@/mocks/data'
import { emitLocalAction } from '@/shared/events'
import { renderWithProviders } from '@/shared/testing'
import BudgetToastWatcher from './BudgetToastWatcher'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function setUp() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning: true }
  seedPlanningFixture(workspace)
  const mercado = mockState.categories.find((each) => each.name === 'Mercado')
  if (!mercado) throw new Error('seed')
  return { workspace, categoryId: mercado.id }
}

function saved(workspaceId: string, categoryId: string, fromQueue = false) {
  act(() =>
    emitLocalAction({
      type: 'transaction-saved',
      workspaceId,
      categoryId,
      month: '2026-11',
      fromQueue,
    }),
  )
}

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

describe('BudgetToastWatcher', () => {
  it('toasts once per threshold', async () => {
    const { workspace, categoryId } = setUp()
    renderWithProviders(<BudgetToastWatcher />)

    saved(workspace.id, categoryId)
    saved(workspace.id, categoryId)

    expect(await screen.findByText('Mercado: chegou a 80% do limite')).toBeInTheDocument()
    await pause(150)
    expect(screen.getAllByText('Mercado: chegou a 80% do limite')).toHaveLength(1)
    expect(
      localStorage.getItem(`liberdade.budget-toast:${workspace.id}:2026-11:${categoryId}:80`),
    ).not.toBeNull()
  })

  it('ignores queued actions', async () => {
    const { workspace, categoryId } = setUp()
    renderWithProviders(<BudgetToastWatcher />)

    saved(workspace.id, categoryId, true)
    await pause(250)

    expect(screen.queryByText(/chegou a 80%/)).not.toBeInTheDocument()
  })

  it('storage failure is silent', async () => {
    const { workspace, categoryId } = setUp()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    renderWithProviders(<BudgetToastWatcher />)

    saved(workspace.id, categoryId)

    expect(await screen.findByText('Mercado: chegou a 80% do limite')).toBeInTheDocument()
  })
})
