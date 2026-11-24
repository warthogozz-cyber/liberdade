import { cleanup, screen } from '@testing-library/react'
import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { emitLocalAction } from '@/shared/events'
import { renderWithProviders } from '@/shared/testing'
import { playAchievement } from '@/shared/sound/playAchievement'
import GoalCelebrationWatcher from './GoalCelebrationWatcher'

vi.mock('@/shared/sound/playAchievement', () => ({
  playAchievement: vi.fn(() => Promise.resolve()),
}))

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.mocked(playAchievement).mockClear()
  localStorage.clear()
})

/** A family with a goal whose contributions already reach its target. */
function setUp() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning: true }
  mockState.goals.push({
    id: crypto.randomUUID(),
    name: 'Viagem',
    icon: 'travel',
    targetCents: 100_000n,
    currency: 'BRL',
    dueOn: null,
    linkedAccountId: null,
    achievedAt: null,
    archived: false,
    version: 0,
    contributions: [
      {
        id: crypto.randomUUID(),
        amount: { amount: '1000.00', currency: 'BRL' },
        memberId: workspace.membershipId,
        occurredOn: '2026-11-10',
      },
    ],
  })
  return workspace
}

const saved = (workspaceId: string, fromQueue: boolean) =>
  act(() => emitLocalAction({ type: 'transaction-saved', workspaceId, fromQueue }))

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

describe('GoalCelebrationWatcher', () => {
  it('celebrates after a local save', async () => {
    const workspace = setUp()
    renderWithProviders(<GoalCelebrationWatcher />)

    saved(workspace.id, false)
    saved(workspace.id, false)

    expect(await screen.findByText('Meta atingida: Viagem!')).toBeInTheDocument()
    await pause(200)
    expect(screen.getAllByText('Meta atingida: Viagem!')).toHaveLength(1)
  })

  it('ignoresQueuedActions', async () => {
    const workspace = setUp()
    renderWithProviders(<GoalCelebrationWatcher />)

    saved(workspace.id, true)
    await pause(250)

    expect(screen.queryByText(/Meta atingida/)).not.toBeInTheDocument()
  })

  it('soundOnlyWhenEnabled', async () => {
    const workspace = setUp()
    renderWithProviders(<GoalCelebrationWatcher />)

    saved(workspace.id, false)
    await screen.findByText('Meta atingida: Viagem!')
    expect(playAchievement).not.toHaveBeenCalled()

    mockState.goals[0].achievedAt = null
    localStorage.clear()
    localStorage.setItem('liberdade.sound', 'on')
    // A new goal reached by the next save.
    mockState.goals.push({ ...mockState.goals[0], id: crypto.randomUUID(), name: 'Reforma' })
    saved(workspace.id, false)
    await screen.findByText('Meta atingida: Reforma!')
    expect(playAchievement).toHaveBeenCalledOnce()
  })
})
