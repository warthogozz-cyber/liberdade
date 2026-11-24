import { act, cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Celebration, CelebrationStack } from './Celebration'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  Reflect.deleteProperty(window, 'matchMedia')
})

function stubReducedMotion(reduced: boolean) {
  window.matchMedia = vi.fn(() => ({
    matches: reduced,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia
}

describe('Celebration', () => {
  it('animatesWithMotion', () => {
    render(<Celebration icon="X" message="Meta batida" onDone={() => undefined} />)

    expect(screen.getByRole('status')).toHaveClass('motion-safe:animate-goal-pop')
  })

  it('stillIconWithReducedMotion', () => {
    stubReducedMotion(true)
    render(<Celebration icon="X" message="Meta batida" onDone={() => undefined} />)

    expect(screen.getByRole('status').className).not.toContain('animate')
    expect(screen.getByRole('status')).toHaveTextContent('Meta batida')
    expect(screen.getByText('X')).toBeInTheDocument()
  })

  it('parentRerenderDoesNotRestartTimer', () => {
    vi.useFakeTimers()
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = render(<Celebration icon="X" message="Oi" onDone={first} />)

    act(() => vi.advanceTimersByTime(5000))
    rerender(<Celebration icon="X" message="Oi" onDone={second} />)
    act(() => vi.advanceTimersByTime(1000))

    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
  })

  it('doesNotBlockTheScreen', () => {
    render(
      <CelebrationStack>
        <p>filho</p>
      </CelebrationStack>,
    )

    expect(screen.getByText('filho').parentElement).toHaveClass('pointer-events-none')
  })
})
