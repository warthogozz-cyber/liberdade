import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { ModuleSlot } from '@/shared/modules'
import { renderWithProviders } from '@/shared/testing'
import './register'

afterEach(cleanup)

function seed(on: boolean, streak: { weeks: number; frozenWeeks: number }) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { gamification: on }
  mockState.streak = { ...streak, longest: streak.weeks }
  return workspace
}

function render() {
  return renderWithProviders(<ModuleSlot name="home" />, { path: '/' })
}

describe('StreakHomeCard', () => {
  it('cartão com sequência', async () => {
    seed(true, { weeks: 3, frozenWeeks: 1 })
    render()

    expect(await screen.findByText('3 semanas seguidas')).toBeInTheDocument()
    expect(screen.getByText('1 semana de folga incluída')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver conquistas' })).toHaveAttribute(
      'href',
      '/mais/conquistas',
    )
  })

  it('cartão sem folga não mostra a linha de folga', async () => {
    seed(true, { weeks: 2, frozenWeeks: 0 })
    render()

    expect(await screen.findByText('2 semanas seguidas')).toBeInTheDocument()
    expect(screen.queryByText(/folga/)).not.toBeInTheDocument()
  })

  it('cartão sem sequência', async () => {
    seed(true, { weeks: 0, frozenWeeks: 0 })
    render()

    expect(
      await screen.findByText('Lance algo esta semana para começar uma sequência.'),
    ).toBeInTheDocument()
    expect(screen.queryByText(/semanas? seguidas?/)).not.toBeInTheDocument()
  })

  it('cartão some com o módulo desligado', async () => {
    const workspace = seed(false, { weeks: 3, frozenWeeks: 1 })
    // Another module is on, so a card wired to the wrong module would show.
    mockState.modules[workspace.id] = { gamification: false, planning: true }
    // Answers like an open module, so only the slot's gate can keep the card away.
    let calls = 0
    server.use(
      http.get(`${API_URL}/v1/workspaces/${workspace.id}/gamification/streak`, () => {
        calls += 1
        return HttpResponse.json({ weeks: 3, frozenWeeks: 1, longest: 3 })
      }),
    )
    render()

    // The tests above are the positive control: the same slot shows the card with the module on.
    await new Promise((resolve) => setTimeout(resolve, 150))
    expect(screen.queryByText('Sequência')).not.toBeInTheDocument()
    expect(screen.queryByText('3 semanas seguidas')).not.toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
