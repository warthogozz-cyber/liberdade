import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const unlock = vi.fn()
const isRunning = vi.fn(() => false)
vi.mock('./synth', () => ({ unlock, isRunning }))

async function fresh() {
  vi.resetModules()
  return import('./unlockSound')
}

beforeEach(() => {
  vi.useFakeTimers()
  unlock.mockClear()
  isRunning.mockReset()
  isRunning.mockReturnValue(false)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('armSoundUnlock', () => {
  it('unlocksOnTheFirstPointerdownAfterTheModuleLoads', async () => {
    const { armSoundUnlock } = await fresh()
    const dispose = armSoundUnlock()

    window.dispatchEvent(new Event('pointerdown'))
    expect(unlock).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1_000)
    window.dispatchEvent(new Event('pointerdown'))
    expect(unlock).toHaveBeenCalledOnce()

    window.dispatchEvent(new Event('keydown'))
    expect(unlock).toHaveBeenCalledTimes(2)

    isRunning.mockReturnValue(true)
    window.dispatchEvent(new Event('pointerdown'))
    window.dispatchEvent(new Event('pointerdown'))
    expect(unlock).toHaveBeenCalledTimes(2)
    dispose()
  })

  it('removesTheListenersOnCleanup', async () => {
    const { armSoundUnlock } = await fresh()
    const dispose = armSoundUnlock()
    await vi.advanceTimersByTimeAsync(1_000)
    dispose()

    window.dispatchEvent(new Event('pointerdown'))
    expect(unlock).not.toHaveBeenCalled()
  })
})

describe('SoundUnlock', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('addsNoListenerAndNoPreloadWithSoundOff', async () => {
    const add = vi.spyOn(window, 'addEventListener')
    const { renderWithProviders } = await import('@/shared/testing')
    const { SoundUnlock } = await import('./SoundUnlock')
    renderWithProviders(<SoundUnlock />, { path: '/' })
    await vi.advanceTimersByTimeAsync(2_000)

    expect(add.mock.calls.filter(([type]) => type === 'pointerdown')).toHaveLength(0)
    window.dispatchEvent(new Event('pointerdown'))
    expect(unlock).not.toHaveBeenCalled()
    add.mockRestore()
  })

  it('armsTheUnlockWithSoundOn', async () => {
    localStorage.setItem('liberdade.sound', 'on')
    const { renderWithProviders } = await import('@/shared/testing')
    const { SoundUnlock } = await import('./SoundUnlock')
    const view = renderWithProviders(<SoundUnlock />, { path: '/' })
    await vi.advanceTimersByTimeAsync(2_000)

    window.dispatchEvent(new Event('pointerdown'))
    expect(unlock).toHaveBeenCalledOnce()
    view.unmount()
  })
})
