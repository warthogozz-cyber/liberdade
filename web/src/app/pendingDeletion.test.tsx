import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { createFamily, mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, selectWorkspace } from '@/shared/api'
import { countOutboxForUser, db } from '@/shared/offline'
import { outboxItem, outboxPayload, renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
})

const ID = '0192f3a4-5b6c-7d8e-9f01-23456789abcd'
const SCHEDULED = '2026-12-16T12:00:00Z'

function App() {
  return useRoutes(routes)
}

describe('pending account deletion', () => {
  it('showsOnlyThePendingPage', async () => {
    seedMockFamily()
    mockState.user.deletionScheduledFor = SCHEDULED
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(await screen.findByText('Sua conta será excluída em 16 de dezembro de 2026.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Baixar meus dados' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('cancelRestores', async () => {
    seedMockFamily()
    mockState.user.deletionScheduledFor = SCHEDULED
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar exclusão' }))

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(mockState.user.deletionScheduledFor).toBeNull()
  })

  it('keepsTheQueuedExpensesWhileTheAccountWaits', async () => {
    const workspace = seedMockFamily()
    mockState.user.deletionScheduledFor = SCHEDULED
    // The API refuses everything of a pending account.
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/transactions`, () =>
        problem(403, 'account.deletion-pending', 'Sua conta será excluída.'),
      ),
    )
    await db.outbox.add(
      outboxItem(ID, {
        userId: mockState.user.id,
        workspaceId: workspace.id,
        payload: outboxPayload(ID, {
          accountId: mockState.accounts[0].id,
          categoryId: mockState.categories[0].id,
        }),
        sendAfter: 0,
      }),
    )
    // The sync starts only once the account is no longer pending: here it is, with the API still refusing.
    mockState.user.deletionScheduledFor = null
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    await waitFor(async () => {
      const item = await db.outbox.get(ID)
      expect(item?.attempts).toBeGreaterThan(0)
    })
    // Refused, but kept to try again, not marked as needing attention.
    const kept = await db.outbox.get(ID)
    expect(kept?.status).toBe('pending')
    expect(await countOutboxForUser(mockState.user.id)).toBe(1)
  })
})

describe('pending family deletion', () => {
  it('showsTheCardOnEveryFamilyRoute', async () => {
    const workspace = seedMockFamily()
    workspace.deletionScheduledFor = SCHEDULED
    renderWithProviders(<App />, { path: '/*', route: '/lancamentos' })

    expect(
      await screen.findByText('Esta família será excluída em 16 de dezembro de 2026.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar exclusão' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Baixar os dados da família' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('cancelBringsTheFamilyBack', async () => {
    const workspace = seedMockFamily()
    workspace.deletionScheduledFor = SCHEDULED
    renderWithProviders(<App />, { path: '/*', route: '/mais' })

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar exclusão' }))

    expect(await screen.findByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
    expect(screen.queryByText(/será excluída/)).not.toBeInTheDocument()
    expect(workspace.deletionScheduledFor).toBeNull()
  })

  it('switcherStillWorks', async () => {
    const other = seedMockFamily()
    const pending = createFamily('Família Bia Teste', 'America/Fortaleza')
    pending.deletionScheduledFor = SCHEDULED
    selectWorkspace(pending.id)
    renderWithProviders(<App />, { path: '/*', route: '/lancamentos' })

    expect(await screen.findByText(/será excluída/)).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Família Teste' }))

    await waitFor(() => expect(screen.queryByText(/será excluída/)).not.toBeInTheDocument())
    expect(other.id).not.toBe(pending.id)
  })
})
