import { afterEach, describe, expect, it, vi } from 'vitest'
import { play } from './synth'

function fakeContext() {
  const oscillators: {
    type: string
    start: ReturnType<typeof vi.fn>
    stop: ReturnType<typeof vi.fn>
  }[] = []
  const gains: {
    gain: {
      value: number
      setValueAtTime: ReturnType<typeof vi.fn>
      linearRampToValueAtTime: ReturnType<typeof vi.fn>
    }
  }[] = []
  const context = {
    currentTime: 0,
    state: 'running',
    destination: {},
    resume: vi.fn(),
    createOscillator: () => {
      const oscillator = {
        type: '',
        frequency: { value: 0 },
        connect: vi.fn(),
        start: vi.fn(),
        stop: vi.fn(),
      }
      oscillators.push(oscillator)
      return oscillator
    },
    createGain: () => {
      const node = {
        connect: vi.fn(),
        gain: {
          value: 0,
          setValueAtTime: vi.fn(),
          linearRampToValueAtTime: vi.fn(),
        },
      }
      gains.push(node)
      return node
    },
  }
  return { context: context as unknown as AudioContext, oscillators, gains }
}

describe('synth', () => {
  it('playsTwoShortQuietNotes', () => {
    const { context, oscillators, gains } = fakeContext()

    play(context)

    expect(oscillators).toHaveLength(2)
    expect(oscillators.map((o) => o.type)).toEqual(['sine', 'sine'])
    const start = Math.min(...oscillators.map((o) => o.start.mock.calls[0][0] as number))
    for (const oscillator of oscillators) {
      expect(oscillator.stop.mock.calls[0][0] - start).toBeLessThanOrEqual(0.3)
    }
    const peaks = gains.flatMap((g) =>
      g.gain.linearRampToValueAtTime.mock.calls.map((call) => call[0] as number),
    )
    expect(Math.max(...peaks)).toBeLessThanOrEqual(0.1)
    expect(Math.max(...peaks)).toBeGreaterThan(0)
  })
})

describe('unlock', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  async function freshSynth(resume: () => Promise<void>) {
    const resumeSpy = vi.fn(resume)
    const created = vi.fn()
    class FakeAudioContext {
      state = 'suspended'
      resume = resumeSpy
      constructor() {
        created()
      }
    }
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.resetModules()
    return { ...(await import('./synth')), resumeSpy, created }
  }

  it('createsAndResumesTheContextThenReusesIt', async () => {
    const { unlock, isRunning, resumeSpy, created } = await freshSynth(() => Promise.resolve())

    unlock()
    unlock()

    expect(created).toHaveBeenCalledOnce()
    expect(resumeSpy).toHaveBeenCalledTimes(2)
    expect(isRunning()).toBe(false)
  })

  it('swallowsAResumeRejection', async () => {
    const unhandled = vi.fn()
    window.addEventListener('unhandledrejection', unhandled)
    const { unlock } = await freshSynth(() => Promise.reject(new Error('blocked')))

    expect(() => {
      unlock()
    }).not.toThrow()
    await new Promise((resolve) => setTimeout(resolve, 10))

    window.removeEventListener('unhandledrejection', unhandled)
    expect(unhandled).not.toHaveBeenCalled()
  })
})
