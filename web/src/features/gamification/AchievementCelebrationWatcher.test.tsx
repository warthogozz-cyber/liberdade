import { cleanup, screen } from '@testing-library/react'
import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { gamificationKeys } from '@/shared/api/gamification'
import { emitLocalAction } from '@/shared/events'
import { playAchievement } from '@/shared/sound/playAchievement'
import { renderWithProviders } from '@/shared/testing'
import AchievementCelebrationWatcher from './AchievementCelebrationWatcher'

vi.mock('@/shared/sound/playAchievement', () => ({
  playAchievement: vi.fn(() => Promise.resolve()),
}))

const NOW = '2026-11-24T15:00:00Z'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.mocked(playAchievement).mockClear()
  localStorage.clear()
})

const open = () => {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { gamification: true }
  mockState.achievements = [{ key: 'FIRST_TRANSACTION', earnedAt: null, silent: false }]
  return workspace
}

const earnNow = () => {
  mockState.achievements = [{ key: 'FIRST_TRANSACTION', earnedAt: NOW, silent: false }]
}

const saved = (workspaceId: string, fromQueue = false) =>
  act(() => emitLocalAction({ type: 'transaction-saved', workspaceId, fromQueue }))

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** Renders and waits until the base list is in the cache. */
async function renderWithBase(workspaceId: string) {
  const view = renderWithProviders(<AchievementCelebrationWatcher />)
  await vi.waitFor(() =>
    expect(view.queryClient.getQueryData(gamificationKeys.achievements(workspaceId))).toBeDefined(),
  )
  return view
}

describe('AchievementCelebrationWatcher', () => {
  it('celebra depois de uma ação local', async () => {
    const workspace = open()
    await renderWithBase(workspace.id)

    earnNow()
    saved(workspace.id)

    expect(await screen.findByRole('status')).toHaveTextContent('Conquista: Primeiro lançamento')
    expect(screen.getAllByRole('status')).toHaveLength(1)
  })

  it('sem base não celebra', async () => {
    const workspace = open()
    renderWithProviders(<AchievementCelebrationWatcher />)
    // The action arrives before the first load has landed.
    earnNow()
    saved(workspace.id)
    await pause(250)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('fila não celebra', async () => {
    const workspace = open()
    await renderWithBase(workspace.id)

    earnNow()
    saved(workspace.id, true)
    await pause(250)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('aviso ao vivo não celebra', async () => {
    const workspace = open()
    const { queryClient } = await renderWithBase(workspace.id)

    earnNow()
    await act(() =>
      queryClient.invalidateQueries({ queryKey: gamificationKeys.achievements(workspace.id) }),
    )
    await pause(250)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(playAchievement).not.toHaveBeenCalled()
  })

  it('som só ligado', async () => {
    const workspace = open()
    await renderWithBase(workspace.id)
    earnNow()
    saved(workspace.id)
    await screen.findByRole('status')
    expect(playAchievement).not.toHaveBeenCalled()
    cleanup()

    localStorage.setItem('liberdade.sound', 'on')
    mockState.achievements = [{ key: 'FIRST_TRANSACTION', earnedAt: null, silent: false }]
    await renderWithBase(workspace.id)
    earnNow()
    saved(workspace.id)
    await screen.findByRole('status')
    expect(playAchievement).toHaveBeenCalledOnce()
  })

  it('outra família', async () => {
    const workspace = open()
    await renderWithBase(workspace.id)

    earnNow()
    saved(crypto.randomUUID())
    await pause(250)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('duas ações seguidas celebram uma vez só', async () => {
    const workspace = open()
    await renderWithBase(workspace.id)

    earnNow()
    saved(workspace.id)
    saved(workspace.id)
    await screen.findByRole('status')
    await pause(250)

    expect(screen.getAllByRole('status')).toHaveLength(1)
  })
})
