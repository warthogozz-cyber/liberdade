import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HomePage } from '@/features/dashboard/HomePage'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, queryKeys, type DashboardView, type MeWorkspace } from '@/shared/api'
import { db } from '@/shared/offline'
import {
  createTestQueryClient,
  outboxItem,
  outboxPayload,
  renderWithProviders,
} from '@/shared/testing'

const DASHBOARD = `${API_URL}/v1/workspaces/:ws/dashboard`

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
  server.use(
    http.get(DASHBOARD, () =>
      HttpResponse.json({
        month: '2026-11',
        expenses: { amount: '10.00', currency: 'BRL' },
        balance: { amount: '90.00', currency: 'BRL' },
      }),
    ),
  )
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function renderHome() {
  return renderWithProviders(<HomePage />, { path: '/inicio' })
}

/** Renders Home with the month's dashboard already cached, as loaded at 11:30 in Fortaleza. */
function renderHomeWithCache(workspace: MeWorkspace) {
  const queryClient = createTestQueryClient()
  const cached: DashboardView = {
    month: '2026-11',
    income: { amount: '0.00', currency: 'BRL' },
    expenses: { amount: '10.00', currency: 'BRL' },
    result: { amount: '-10.00', currency: 'BRL' },
    balance: { amount: '90.00', currency: 'BRL' },
    byCategory: [],
    dailyBalance: [],
  }
  queryClient.setQueryData(queryKeys.dashboard(workspace.id, '2026-11'), cached, {
    updatedAt: Date.parse('2026-11-15T14:30:00Z'),
  })
  return renderWithProviders(<HomePage />, { path: '/inicio', queryClient })
}

function queue(workspace: MeWorkspace, id: string, overrides: Parameters<typeof outboxItem>[1]) {
  return outboxItem(id, { userId: mockState.user.id, workspaceId: workspace.id, ...overrides })
}

describe('HomePage', () => {
  it('adds the pending expenses to the month and takes them from the balance', async () => {
    const workspace = seedMockFamily()
    await db.outbox.bulkAdd([
      queue(workspace, 't1', { payload: outboxPayload('t1', { occurredOn: '2026-11-10' }) }),
      queue(workspace, 't2', {
        createdAt: 2000,
        status: 'needs-attention',
        payload: outboxPayload('t2', { amount: { amount: '7.00', currency: 'BRL' } }),
      }),
    ])
    renderHome()

    expect(screen.getByRole('heading', { level: 1, name: 'Início' })).toBeInTheDocument()
    expect(await screen.findByText('R$ 22,34')).toBeInTheDocument()
    expect(screen.getByText('Gastos em novembro')).toBeInTheDocument()
    expect(screen.getByText('Saldo total')).toBeInTheDocument()
    expect(screen.getByText('R$ 77,66')).toBeInTheDocument()
    expect(screen.getByText('Adicione suas contas para ver o saldo real.')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '1 gasto precisa de atenção' })).toHaveAttribute(
      'href',
      '/lancamentos',
    )
  })

  it('queuedIncomeRaisesBalance', async () => {
    const workspace = seedMockFamily()
    await db.outbox.bulkAdd([
      queue(workspace, 't1', {
        payload: outboxPayload('t1', {
          kind: 'INCOME',
          amount: { amount: '100.00', currency: 'BRL' },
          occurredOn: '2026-11-10',
        }),
      }),
      queue(workspace, 't2', {
        createdAt: 2000,
        payload: outboxPayload('t2', {
          kind: 'TRANSFER',
          amount: { amount: '30.00', currency: 'BRL' },
          occurredOn: '2026-11-10',
          categoryId: null,
          toAccountId: 'other-account',
        }),
      }),
    ])
    renderHome()

    // The month's expenses stay as they are; only the balance takes the income.
    expect(await screen.findByText('R$ 190,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 10,00')).toBeInTheDocument()
  })

  it('shows the dashboard as it is with an empty queue', async () => {
    seedMockFamily()
    renderHome()

    expect(await screen.findByText('R$ 10,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 90,00')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /atenção/ })).not.toBeInTheDocument()
  })

  it('shows the first steps, with the link to create a passkey', async () => {
    seedMockFamily()
    renderHome()

    expect(await screen.findByRole('heading', { name: 'Primeiros passos' })).toBeInTheDocument()
    expect(
      screen.getByText('Crie uma passkey para entrar mais rápido, sem senha.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Criar passkey' })).toHaveAttribute('href', '/mais')
  })

  it('keeps the first steps closed once dismissed', async () => {
    seedMockFamily()
    const { unmount } = renderHome()

    fireEvent.click(await screen.findByRole('button', { name: 'Fechar primeiros passos' }))
    expect(screen.queryByRole('heading', { name: 'Primeiros passos' })).not.toBeInTheDocument()
    unmount()

    renderHome()
    expect(await screen.findByText('R$ 10,00')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Primeiros passos' })).not.toBeInTheDocument()
  })

  it('leaves the first steps out once there is a passkey', async () => {
    seedMockFamily()
    mockState.user.passkeyCreatedAt = '2026-11-01T12:00:00Z'
    renderHome()

    expect(await screen.findByText('R$ 10,00')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Primeiros passos' })).not.toBeInTheDocument()
  })

  it('says the totals wait for the signal when offline without a cache', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    server.use(http.get(DASHBOARD, () => HttpResponse.error()))
    renderHome()

    expect(
      await screen.findByText('Sem conexão. Os totais aparecem quando o sinal voltar.'),
    ).toBeInTheDocument()
  })

  it('says when the cached totals are from while offline (I3)', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const workspace = seedMockFamily()
    server.use(http.get(DASHBOARD, () => HttpResponse.error()))
    renderHomeWithCache(workspace)

    expect(await screen.findByText('R$ 10,00')).toBeInTheDocument()
    expect(await screen.findByText('Mostrando dados de 15/11, 11:30.')).toBeInTheDocument()
  })

  it('leaves the data date out while online (I3)', async () => {
    const workspace = seedMockFamily()
    renderHomeWithCache(workspace)

    expect(await screen.findByText('R$ 10,00')).toBeInTheDocument()
    expect(screen.queryByText(/Mostrando dados/)).not.toBeInTheDocument()
  })

  it('offers to try again when the dashboard fails', async () => {
    seedMockFamily()
    server.use(http.get(DASHBOARD, () => problem(500, 'internal.error'), { once: true }))
    renderHome()

    expect(await screen.findByText('Não deu para carregar. Tente de novo.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    await waitFor(() => expect(screen.getByText('R$ 10,00')).toBeInTheDocument())
  })
})
