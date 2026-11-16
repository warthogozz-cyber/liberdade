import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { startAuthentication } from '@simplewebauthn/browser'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

vi.mock('@simplewebauthn/browser')

beforeEach(() => {
  vi.mocked(startAuthentication).mockResolvedValue({ id: 'credential' } as never)
})

describe('pending account deletion', () => {
  it('showsOnlyThePendingPage', async () => {
    seedMockFamily()
    mockState.user.deletionScheduledFor = SCHEDULED
    renderWithProviders(null, { routes, route: '/inicio' })

    expect(await screen.findByText('Sua conta será excluída em 16 de dezembro de 2026.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Baixar meus dados' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('cancelRestores', async () => {
    seedMockFamily()
    mockState.user.deletionScheduledFor = SCHEDULED
    const { router } = renderWithProviders(null, { routes, route: '/mais/privacidade' })

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar exclusão' }))

    expect(await screen.findByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(mockState.user.deletionScheduledFor).toBeNull()
  })

  it('schedulingShowsThePendingScreen', async () => {
    seedMockFamily()
    let calls = 0
    server.use(
      http.post(`${API_URL}/v1/me/deletion`, () =>
        (calls += 1) === 1
          ? problem(403, 'auth.reauthentication-required', 'Confirme que é você.')
          : undefined,
      ),
    )
    renderWithProviders(null, { routes, route: '/mais/privacidade' })

    fireEvent.click(await screen.findByRole('button', { name: 'Excluir minha conta' }))
    await screen.findByRole('dialog')
    fireEvent.click(screen.getAllByRole('button', { name: 'Excluir minha conta' }).at(-1)!)
    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    expect(await screen.findByText(/Sua conta será excluída em/)).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Histórico de acessos' })).not.toBeInTheDocument()
  })

  it('keepsTheQueuedExpensesThenFlushesAfterCancel', async () => {
    const workspace = seedMockFamily()
    mockState.user.deletionScheduledFor = SCHEDULED
    let refuse = true
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/transactions`, () =>
        refuse
          ? problem(403, 'account.deletion-pending', 'Sua conta será excluída.')
          : undefined,
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
    renderWithProviders(null, { routes, route: '/inicio' })

    // The account is pending and the API refuses: the item stays queued, not marked as failed.
    await waitFor(async () => {
      expect((await db.outbox.get(ID))?.attempts).toBeGreaterThan(0)
    })
    const kept = await db.outbox.get(ID)
    expect(kept?.status).toBe('pending')
    expect(kept?.sendAfter).toBeGreaterThan(Date.now())

    // Cancelling brings the wait to an end and the queue goes out at once.
    refuse = false
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar exclusão' }))
    await waitFor(async () => expect(await countOutboxForUser(mockState.user.id)).toBe(0))
  })
})

describe('privacy routes', () => {
  it.each([
    ['/mais/privacidade', 'Histórico de acessos'],
    ['/mais/privacidade/atividade', 'Voltar para a privacidade'],
  ])('opens %s', async (route, link) => {
    seedMockFamily()
    renderWithProviders(null, { routes, route })

    expect(await screen.findByRole('link', { name: link })).toBeInTheDocument()
  })

  it('unknownSubpathIsNotFound', async () => {
    seedMockFamily()
    renderWithProviders(null, { routes, route: '/mais/privacidade/qualquer-coisa' })

    expect(await screen.findByText('Página não encontrada.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Histórico de acessos' })).not.toBeInTheDocument()
  })
})

describe('pending family deletion', () => {
  it('showsTheCardOnEveryFamilyRoute', async () => {
    const workspace = seedMockFamily()
    workspace.deletionScheduledFor = SCHEDULED
    renderWithProviders(null, { routes, route: '/lancamentos' })

    expect(
      await screen.findByText('Esta família será excluída em 16 de dezembro de 2026.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar exclusão' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Baixar os dados da família' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('cancelBringsTheFamilyBack', async () => {
    const workspace = seedMockFamily()
    workspace.deletionScheduledFor = SCHEDULED
    renderWithProviders(null, { routes, route: '/mais' })

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
    renderWithProviders(null, { routes, route: '/lancamentos' })

    expect(await screen.findByText(/será excluída/)).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Família Teste' }))

    await waitFor(() => expect(screen.queryByText(/será excluída/)).not.toBeInTheDocument())
    expect(other.id).not.toBe(pending.id)
  })
})
