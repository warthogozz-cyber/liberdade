import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { GoalCelebration } from './GoalCelebration'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function show() {
  return renderWithProviders(<GoalCelebration name="Viagem" icon="travel" onDone={() => {}} />)
}

function stubMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: reduce && query.includes('reduce'),
    media: query,
  }))
}

describe('GoalCelebration', () => {
  it('announces the goal with a calm animation', () => {
    stubMotion(false)
    show()

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Meta atingida: Viagem!')
    expect(status.outerHTML).toContain('animate')
  })

  it('reduced motion is static', () => {
    stubMotion(true)
    show()

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Meta atingida: Viagem!')
    expect(status.outerHTML).not.toContain('animate')
  })
})
