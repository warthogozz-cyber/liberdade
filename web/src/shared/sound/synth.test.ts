import { describe, expect, it, vi } from 'vitest'
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
