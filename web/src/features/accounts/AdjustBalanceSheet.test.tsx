import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { AdjustBalanceSheet } from './AdjustBalanceSheet'

afterEach(cleanup)

describe('AdjustBalanceSheet', () => {
  it('saves a negative balance and closes', async () => {
    const workspace = seedMockFamily()
    const stored = mockState.accounts[0]
    const captured: { body?: unknown } = {}
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/accounts/:id`, async ({ request }) => {
        captured.body = await request.clone().json()
      }),
    )
    const onClose = vi.fn()
    renderWithProviders(
      <AdjustBalanceSheet
        workspace={workspace}
        account={{ ...stored, balance: stored.initialBalance }}
        onClose={onClose}
      />,
    )

    expect(screen.getByText('Qual é o saldo hoje?')).toBeInTheDocument()
    for (const key of ['1', '0', '0']) fireEvent.click(screen.getByRole('button', { name: key }))
    fireEvent.click(screen.getByRole('switch', { name: 'Saldo negativo' }))
    expect(screen.getByText('−R$ 1,00')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(captured.body).toEqual({ balance: { amount: '-1.00', currency: workspace.currency } })
  })
})
