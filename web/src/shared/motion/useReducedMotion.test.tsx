import { act, cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useReducedMotion } from './useReducedMotion'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // jsdom has no matchMedia of its own.
  Reflect.deleteProperty(window, 'matchMedia')
})

function Probe() {
  return <p>{useReducedMotion() ? 'reduced' : 'full'}</p>
}

describe('useReducedMotion', () => {
  it('followsTheMediaQuery', () => {
    let listener: (() => void) | undefined
    const list = {
      matches: false,
      addEventListener: (_type: string, fn: () => void) => {
        listener = fn
      },
      removeEventListener: () => {
        listener = undefined
      },
    }
    window.matchMedia = vi.fn(() => list) as unknown as typeof window.matchMedia
    render(<Probe />)
    expect(screen.getByText('full')).toBeInTheDocument()

    list.matches = true
    act(() => listener?.())

    expect(screen.getByText('reduced')).toBeInTheDocument()
  })

  it('falseWithoutMatchMedia', () => {
    render(<Probe />)

    expect(screen.getByText('full')).toBeInTheDocument()
  })
})
