import { cleanup, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
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
    seed(false, { weeks: 3, frozenWeeks: 1 })
    render()

    await waitFor(() => expect(screen.queryByText('Sequência')).not.toBeInTheDocument())
    expect(screen.queryByText('3 semanas seguidas')).not.toBeInTheDocument()
  })
})
