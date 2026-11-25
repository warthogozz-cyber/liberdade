import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { AchievementsPage } from './AchievementsPage'

afterEach(cleanup)

describe('AchievementsPage', () => {
  it('tela separa ganhas e próximas', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { gamification: true }
    mockState.streak = { weeks: 3, frozenWeeks: 0, longest: 3 }
    mockState.achievements = [
      { key: 'FIRST_TRANSACTION', earnedAt: '2026-11-10T15:00:00Z', silent: false },
      { key: 'STREAK_4_WEEKS', earnedAt: null, silent: false },
      { key: 'FIRST_GOAL', earnedAt: null, silent: false },
    ]
    renderWithProviders(<AchievementsPage />, { path: '/mais/conquistas' })

    const earned = (await screen.findByRole('heading', { name: 'Ganhas' })).closest('section')!
    expect(within(earned).getByText('Primeiro lançamento')).toBeInTheDocument()
    expect(within(earned).getByText('Ganha em 10/11/2026')).toBeInTheDocument()

    const next = screen.getByRole('heading', { name: 'Próximas' }).closest('section')!
    expect(within(next).getByText('4 semanas seguidas')).toBeInTheDocument()
    expect(within(next).getByText('Registre algo em 4 semanas seguidas.')).toBeInTheDocument()
    expect(within(next).getByText('Primeira meta')).toBeInTheDocument()
    expect(within(next).getByText('Crie uma meta em Planejar.')).toBeInTheDocument()
    expect(within(next).queryByText(/Ganha em/)).not.toBeInTheDocument()

    expect(screen.getByText('3 semanas seguidas')).toBeInTheDocument()
    expect(document.querySelector('[data-tour="achievements-streak"]')).not.toBeNull()
    expect(document.querySelector('[data-tour="achievements-list"]')).not.toBeNull()
  })
})
