import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, queryKeys } from '@/shared/api'
import { transactionKey } from '@/shared/api/detail'
import {
  createTestQueryClient,
  outboxPayload,
  renderWithProviders,
  transactionView,
} from '@/shared/testing'
import { NewTransactionPage } from './NewTransactionPage'

const EDIT_ID = '0192f3a4-0000-7000-8000-0000000000e1'
const GET_URL = `${API_URL}/v1/workspaces/:ws/transactions/:id`

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

/** A transaction of `amount` in the mock API, and a copy of it of `listed` in the month's list. */
function setUp(amount: string, listed = amount) {
  const workspace = seedMockFamily()
  const body = outboxPayload(EDIT_ID, {
    accountId: mockState.accounts[0].id,
    categoryId: mockState.categories.find((each) => each.name === 'Mercado')!.id,
    amount: { amount, currency: 'BRL' },
  })
  mockState.transactions.push({ ...transactionView(body), memberId: workspace.membershipId })
  const queryClient = createTestQueryClient()
  queryClient.setQueryData(queryKeys.transactions(workspace.id, '2026-11'), [
    {
      ...transactionView({ ...body, amount: { amount: listed, currency: 'BRL' } }),
      memberId: workspace.membershipId,
    },
  ])
  return { workspace, queryClient }
}

function renderEdit(queryClient = createTestQueryClient()) {
  return renderWithProviders(<NewTransactionPage />, {
    path: '/lancar',
    route: `/lancar?editar=${EDIT_ID}&mes=2026-11`,
    queryClient,
  })
}

/** Puts an earlier read of the transaction, at `amount`, in the cache: the copy of a past visit. */
function seedEarlierRead(workspaceId: string, amount: string) {
  const queryClient = createTestQueryClient()
  const body = outboxPayload(EDIT_ID, {
    accountId: mockState.accounts[0].id,
    categoryId: mockState.categories.find((each) => each.name === 'Mercado')!.id,
    amount: { amount, currency: 'BRL' },
  })
  queryClient.setQueryData(transactionKey(workspaceId, EDIT_ID), transactionView(body))
  return queryClient
}

describe('EditTransaction', () => {
  it('neverOpensTheCachedCopyBeforeTheServerAnswers', async () => {
    const { workspace } = setUp('12.00')
    renderEdit(seedEarlierRead(workspace.id, '10.00'))

    // The earlier read is in the cache, but the form waits for this visit's.
    expect(screen.queryByRole('status', { name: 'Valor' })).not.toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('status', { name: 'Valor' })).toHaveTextContent('R$ 12,00'),
    )
  })

  it('doesNotOpenAnEarlierReadOfADeletedTransaction', async () => {
    const { workspace } = setUp('12.00')
    mockState.deletedTransactionIds.add(EDIT_ID)
    renderEdit(seedEarlierRead(workspace.id, '10.00'))

    expect(await screen.findByText('Este lançamento foi excluído')).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Valor' })).not.toBeInTheDocument()
  })

  it('loadsFreshCopy', async () => {
    const { queryClient } = setUp('12.00', '10.00')
    renderEdit(queryClient)

    await waitFor(() =>
      expect(screen.getByRole('status', { name: 'Valor' })).toHaveTextContent('R$ 12,00'),
    )
  })

  it('deletedMessage', async () => {
    const { queryClient } = setUp('12.00')
    mockState.deletedTransactionIds.add(EDIT_ID)
    renderEdit(queryClient)

    expect(await screen.findByText('Este lançamento foi excluído')).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Valor' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar para os lançamentos' })).toHaveAttribute(
      'href',
      '/lancamentos?mes=2026-11',
    )
  })

  it('goneMessageRefreshesTheList', async () => {
    const { workspace, queryClient } = setUp('12.00')
    mockState.transactions.length = 0
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    renderEdit(queryClient)

    expect(await screen.findByText('Este lançamento não existe mais')).toBeInTheDocument()
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: queryKeys.transactions(workspace.id, '2026-11'),
    })
  })

  it('fallsBackToTheListWhenTheReadFails', async () => {
    const { queryClient } = setUp('12.00', '10.00')
    server.use(http.get(GET_URL, () => problem(400, 'request.malformed')))
    renderEdit(queryClient)

    await waitFor(() =>
      expect(screen.getByRole('status', { name: 'Valor' })).toHaveTextContent('R$ 10,00'),
    )
  })
})

const PATCH_URL = `${API_URL}/v1/workspaces/:ws/transactions/:id`

/** Records each PATCH body and lets the mock API answer it. */
function recordPatches() {
  const bodies: Record<string, unknown>[] = []
  server.use(
    http.patch(PATCH_URL, async ({ request }) => {
      bodies.push((await request.clone().json()) as Record<string, unknown>)
      return undefined
    }),
  )
  return bodies
}

/** A Mercado expense of R$ 12,00 marked with the project, in the mock API. */
async function openMarked(withBank = false) {
  const workspace = seedMockFamily()
  if (withBank) {
    mockState.accounts.push({
      ...mockState.accounts[0],
      id: crypto.randomUUID(),
      name: 'Banco',
      ownerMembershipId: workspace.membershipId,
    })
  }
  const venture = seedVentureFixture(workspace)
  const body = outboxPayload(EDIT_ID, {
    accountId: mockState.accounts[0].id,
    categoryId: mockState.categories.find((each) => each.name === 'Mercado')!.id,
    amount: { amount: '12.00', currency: 'BRL' },
    ventureId: venture.id,
  })
  mockState.transactions.push({ ...transactionView(body), memberId: workspace.membershipId })
  const rendered = renderEdit()
  await waitFor(() =>
    expect(screen.getByRole('status', { name: 'Valor' })).toHaveTextContent('R$ 12,00'),
  )
  return { workspace, venture, ...rendered }
}

const saveButton = () => screen.getByRole('button', { name: 'Salvar' })

describe('EditTransaction project', () => {
  it('untouchedVentureSendsNothing', async () => {
    const bodies = recordPatches()
    const { venture, router } = await openMarked()
    const field = await screen.findByRole('combobox', { name: 'Projeto (opcional)' })
    expect(field).toHaveValue(venture.id)

    fireEvent.click(screen.getByRole('button', { name: '5' }))
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancamentos'))
    expect(bodies).toHaveLength(1)
    expect(bodies[0]).not.toHaveProperty('ventureId')
    expect(bodies[0]).not.toHaveProperty('clearVenture')
  })

  it('choosingNoneSendsClear', async () => {
    const bodies = recordPatches()
    const { router } = await openMarked()
    const field = await screen.findByRole('combobox', { name: 'Projeto (opcional)' })

    fireEvent.change(field, { target: { value: '' } })
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancamentos'))
    expect(bodies[0]).toMatchObject({ clearVenture: true })
    expect(bodies[0]).not.toHaveProperty('ventureId')
    expect(mockState.transactions[0].ventureId).toBeNull()
  })

  it('switchingToTransferClears', async () => {
    const bodies = recordPatches()
    const { router } = await openMarked(true)
    await screen.findByRole('combobox', { name: 'Projeto (opcional)' })

    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    expect(screen.queryByRole('combobox', { name: 'Projeto (opcional)' })).not.toBeInTheDocument()
    fireEvent.click(
      within(screen.getByRole('group', { name: 'Para' })).getByRole('radio', { name: 'Banco' }),
    )
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancamentos'))
    expect(bodies[0]).toMatchObject({ kind: 'TRANSFER', clearVenture: true })
    expect(bodies[0]).not.toHaveProperty('ventureId')
  })
})
