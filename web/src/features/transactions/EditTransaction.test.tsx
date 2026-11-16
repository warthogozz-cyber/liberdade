import { cleanup, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, queryKeys } from '@/shared/api'
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

describe('EditTransaction', () => {
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
