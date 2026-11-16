import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { onlineManager } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TransactionsPage } from '@/features/transactions/TransactionsPage'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, type CreateTransactionRequest, type MeWorkspace } from '@/shared/api'
import { db } from '@/shared/offline'
import {
  createTestQueryClient,
  outboxItem,
  outboxPayload,
  renderWithProviders,
  transactionView,
} from '@/shared/testing'

const MERCADO_ID = '0192f3a4-0000-7000-8000-0000000000b1'
const PENDING_ID = '0192f3a4-0000-7000-8000-0000000000b2'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function renderTransactions() {
  return renderWithProviders(<TransactionsPage />, { path: '/lancamentos' })
}

const categoryId = (name: string) => mockState.categories.find((each) => each.name === name)!.id

/** A request in the wallet, for the category `name`. */
function request(id: string, name: string, overrides: Partial<CreateTransactionRequest> = {}) {
  return outboxPayload(id, {
    accountId: mockState.accounts[0].id,
    categoryId: categoryId(name),
    ...overrides,
  })
}

/** Stores `body` in the mock API as the signed-in member's. */
function storeOnServer(workspace: MeWorkspace, body: CreateTransactionRequest) {
  mockState.transactions.push({
    ...transactionView(body, '2026-11-15T14:00:00Z'),
    memberId: workspace.membershipId,
  })
}

function queue(workspace: MeWorkspace, id: string, overrides: Parameters<typeof outboxItem>[1]) {
  return outboxItem(id, { userId: mockState.user.id, workspaceId: workspace.id, ...overrides })
}

const day = (title: string) => screen.findByRole('list', { name: title })

/** Queues a refused expense and opens its sheet. */
async function openAttention(workspace: MeWorkspace) {
  await db.outbox.add(
    queue(workspace, PENDING_ID, {
      status: 'needs-attention',
      attempts: 1,
      problem: { code: 'transaction.account-not-found', detail: 'Conta arquivada.' },
      payload: request(PENDING_ID, 'Transporte', { occurredOn: '2026-11-14' }),
    }),
  )
  const rendered = renderTransactions()
  fireEvent.click(await screen.findByRole('button', { name: 'Precisa de atenção' }))
  return { ...rendered, sheet: screen.getByRole('dialog', { name: 'Este gasto não foi salvo' }) }
}

describe('TransactionsPage', () => {
  it('lists the API and the queue by day, the queued one waiting for the signal', async () => {
    const workspace = seedMockFamily()
    storeOnServer(
      workspace,
      request(MERCADO_ID, 'Mercado', {
        amount: { amount: '87.90', currency: 'BRL' },
        occurredOn: '2026-11-15',
      }),
    )
    await db.outbox.add(
      queue(workspace, PENDING_ID, {
        payload: request(PENDING_ID, 'Transporte', { occurredOn: '2026-11-14' }),
      }),
    )
    renderTransactions()

    expect(screen.getByRole('heading', { level: 1, name: 'Lançamentos' })).toBeInTheDocument()
    const today = within(await day('Hoje'))
    expect(today.getByText('🛒 Mercado')).toBeInTheDocument()
    expect(today.getByText('−R$ 87,90')).toBeInTheDocument()
    expect(today.getByText('Carteira · Ana')).toBeInTheDocument()
    expect(today.queryByText('Aguardando sinal')).not.toBeInTheDocument()
    const yesterday = within(await day('Ontem'))
    expect(yesterday.getByText('🚌 Transporte')).toBeInTheDocument()
    expect(yesterday.getByText('Aguardando sinal')).toBeInTheDocument()
    expect(document.title).toBe('Lançamentos · Liberdade')
  })

  it('shows one row, without the badge, for an id both sent and still queued', async () => {
    const workspace = seedMockFamily()
    const body = request(MERCADO_ID, 'Mercado', { occurredOn: '2026-11-15' })
    storeOnServer(workspace, body)
    await db.outbox.add(queue(workspace, MERCADO_ID, { payload: body }))
    renderTransactions()

    const today = within(await day('Hoje'))
    expect(today.getAllByRole('listitem')).toHaveLength(1)
    expect(screen.queryByText('Aguardando sinal')).not.toBeInTheDocument()
  })

  it('shows income with a plus sign', async () => {
    const workspace = seedMockFamily()
    storeOnServer(
      workspace,
      request(MERCADO_ID, 'Salário', {
        kind: 'INCOME',
        amount: { amount: '5000.00', currency: 'BRL' },
        occurredOn: '2026-11-15',
      }),
    )
    renderTransactions()

    const today = within(await day('Hoje'))
    expect(today.getByText('💼 Salário')).toBeInTheDocument()
    expect(today.getByText('+R$ 5.000,00')).toBeInTheDocument()
  })

  it('says Sem categoria for a category it does not know', async () => {
    const workspace = seedMockFamily()
    storeOnServer(
      workspace,
      outboxPayload(MERCADO_ID, {
        accountId: mockState.accounts[0].id,
        categoryId: '0192f3a4-0000-7000-8000-0000000000c9',
        occurredOn: '2026-11-15',
      }),
    )
    renderTransactions()

    expect(within(await day('Hoje')).getByText('Sem categoria')).toBeInTheDocument()
  })

  it('lists a queued expense of another month under its date', async () => {
    const workspace = seedMockFamily()
    await db.outbox.add(
      queue(workspace, PENDING_ID, {
        payload: request(PENDING_ID, 'Mercado', { occurredOn: '2026-10-20' }),
      }),
    )
    renderTransactions()

    const october = within(await day('20 de outubro'))
    expect(october.getByText('🛒 Mercado')).toBeInTheDocument()
    expect(october.getByText('Aguardando sinal')).toBeInTheDocument()
  })

  it('explains a refused expense and links to fixing it', async () => {
    const workspace = seedMockFamily()
    const { router, sheet } = await openAttention(workspace)

    expect(within(sheet).getByText('Conta arquivada.')).toBeInTheDocument()
    fireEvent.click(within(sheet).getByRole('link', { name: 'Corrigir' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancar'))
    expect(router.state.location.search).toBe(`?corrigir=${PENDING_ID}`)
  })

  it('discards a refused expense once confirmed', async () => {
    const workspace = seedMockFamily()
    const { sheet } = await openAttention(workspace)

    fireEvent.click(within(sheet).getByRole('button', { name: 'Descartar' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Descartar este gasto?' })
    expect(
      within(dialog).getByText('Ele não foi salvo e vai sumir deste aparelho.'),
    ).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Descartar' }))

    await waitFor(() => expect(screen.queryByText('🚌 Transporte')).not.toBeInTheDocument())
    expect(await db.outbox.count()).toBe(0)
  })

  it('says something went wrong when discarding fails', async () => {
    const workspace = seedMockFamily()
    const { sheet } = await openAttention(workspace)
    fireEvent.click(within(sheet).getByRole('button', { name: 'Descartar' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Descartar este gasto?' })
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(db, 'transaction').mockRejectedValueOnce(new Error('broken'))

    fireEvent.click(within(dialog).getByRole('button', { name: 'Descartar' }))

    expect(
      await within(screen.getByRole('region', { name: 'Notificações' })).findByText(
        'Não deu para carregar. Tente de novo.',
      ),
    ).toBeInTheDocument()
    expect(consoleError).toHaveBeenCalled()
    expect(await db.outbox.count()).toBe(1)
  })

  it('keeps a refused expense with Manter', async () => {
    const workspace = seedMockFamily()
    const { sheet } = await openAttention(workspace)

    fireEvent.click(within(sheet).getByRole('button', { name: 'Descartar' }))
    const dialog = await screen.findByRole('alertdialog', { name: 'Descartar este gasto?' })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Manter' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(await db.outbox.count()).toBe(1)
    expect(screen.getByText('🚌 Transporte')).toBeInTheDocument()
  })

  it('says expenses wait for the signal while offline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    renderTransactions()

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Sem conexão. Você pode lançar; os gastos saem quando o sinal voltar.',
    )
  })

  it('does not keep loading while offline without a cache', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    server.use(http.get(`${API_URL}/v1/workspaces/:ws/transactions`, () => HttpResponse.error()))
    // The app retries, and a retry while offline pauses the query instead of failing it.
    const queryClient = createTestQueryClient()
    queryClient.setDefaultOptions({
      queries: { ...queryClient.getDefaultOptions().queries, retry: 1, retryDelay: 0 },
    })
    onlineManager.setOnline(false)
    try {
      renderWithProviders(<TransactionsPage />, { path: '/lancamentos', queryClient })

      expect(await screen.findByRole('status')).toBeInTheDocument()
      await waitFor(() =>
        expect(
          queryClient
            .getQueryCache()
            .getAll()
            .some((query) => query.state.fetchStatus === 'paused'),
        ).toBe(true),
      )
      expect(screen.queryByText('Carregando…')).not.toBeInTheDocument()
    } finally {
      onlineManager.setOnline(true)
    }
  })

  it('offers to try again when the list fails, keeping the queue listed', async () => {
    const workspace = seedMockFamily()
    storeOnServer(workspace, request(MERCADO_ID, 'Mercado', { occurredOn: '2026-11-15' }))
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/transactions`, () => problem(500, 'internal.error'), {
        once: true,
      }),
    )
    await db.outbox.add(
      queue(workspace, PENDING_ID, {
        payload: request(PENDING_ID, 'Transporte', { occurredOn: '2026-11-14' }),
      }),
    )
    renderTransactions()

    expect(await screen.findByText('Não deu para carregar. Tente de novo.')).toBeInTheDocument()
    expect(within(await day('Ontem')).getByText('🚌 Transporte')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    expect(within(await day('Hoje')).getByText('🛒 Mercado')).toBeInTheDocument()
    expect(screen.queryByText('Não deu para carregar. Tente de novo.')).not.toBeInTheDocument()
    expect(screen.getByText('🚌 Transporte')).toBeInTheDocument()
  })

  it('says there is nothing this month', async () => {
    seedMockFamily()
    renderTransactions()

    expect(await screen.findByText('Nenhum lançamento neste mês.')).toBeInTheDocument()
  })
})
