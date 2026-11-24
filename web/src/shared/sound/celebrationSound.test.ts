import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { emitLocalAction } from '@/shared/events'
import { celebrationSound } from './celebrationSound'
import { playAchievement } from './playAchievement'

vi.mock('./playAchievement', () => ({ playAchievement: vi.fn(() => Promise.resolve()) }))

const NOW = 1_000_000

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
  localStorage.clear()
})

const local = (workspaceId: string) =>
  emitLocalAction({ type: 'transaction-saved', workspaceId, fromQueue: false })

describe('celebrationSound', () => {
  it('noSoundWhenOff', () => {
    local('ws-off')

    celebrationSound('ws-off')

    expect(playAchievement).not.toHaveBeenCalled()
  })

  it('playsRightAfterLocalAction', () => {
    localStorage.setItem('liberdade.sound', 'on')
    local('ws-on')

    celebrationSound('ws-on', NOW + 3_000)

    expect(playAchievement).toHaveBeenCalledOnce()
  })

  it('noSoundForLiveOnlyCelebration', () => {
    localStorage.setItem('liberdade.sound', 'on')

    celebrationSound('ws-never')
    expect(playAchievement).not.toHaveBeenCalled()

    local('ws-late')
    celebrationSound('ws-late', NOW + 11_000)
    expect(playAchievement).not.toHaveBeenCalled()
  })
})
