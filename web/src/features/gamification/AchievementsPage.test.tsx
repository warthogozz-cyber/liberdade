import { cleanup, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { AchievementsPage } from './AchievementsPage'

afterEach(cleanup)

describe('AchievementsPage', () => {
  it('tela separa ganhas e próximas', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { gamification: true }
    mockState.streak = { weeks: 3, frozenWeeks: 0, longest: 3 }
    mockState.achievements = [
      { key: 'FIRST_TRANSACTION', earnedAt: '2026-11-10T12:00:00Z', silent: false },
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

  it('sequência mostra carregando e depois o erro', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { gamification: true }
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/gamification/streak`, () =>
        HttpResponse.json({ code: 'internal' }, { status: 500 }),
      ),
    )
    renderWithProviders(<AchievementsPage />, { path: '/mais/conquistas' })

    expect(screen.getByLabelText('Carregando a sequência…')).toHaveAttribute('aria-busy', 'true')
    expect(await screen.findByText('Não deu para ver a sequência agora.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Carregando a sequência…')).not.toBeInTheDocument()
  })
})

describe('AchievementsPage tour', () => {
  beforeEach(() => {
    localStorage.setItem('liberdade.tips', 'on')
    Element.prototype.scrollIntoView = () => {}
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('tourHasBothStepsWhileLoading', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { gamification: true }
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/gamification/achievements`, async () => {
        await new Promise(() => {})
        return HttpResponse.json({})
      }),
    )
    renderWithProviders(<AchievementsPage />, { path: '/mais/conquistas' })

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Passo 1 de 2')
    expect(document.querySelectorAll('[data-tour="achievements-list"]')).toHaveLength(1)
    expect(document.querySelector('[data-tour="achievements-list"]')).toHaveAttribute(
      'aria-busy',
      'true',
    )
  })

  it('tourHasBothStepsOnError', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { gamification: true }
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/gamification/achievements`, () =>
        HttpResponse.json({ code: 'internal' }, { status: 500 }),
      ),
    )
    renderWithProviders(<AchievementsPage />, { path: '/mais/conquistas' })

    await screen.findByRole('button', { name: 'Tentar de novo' })
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Passo 1 de 2')
    expect(document.querySelectorAll('[data-tour="achievements-list"]')).toHaveLength(1)
  })
})
