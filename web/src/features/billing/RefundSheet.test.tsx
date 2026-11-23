import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { RefundSheet } from './RefundSheet'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('RefundSheet', () => {
  it('confirmsWithoutReason', async () => {
    const workspace = seedMockFamily()
    let body: string | undefined
    server.use(
      http.post(
        `${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refund`,
        async ({ request }) => {
          body = await request.text()
          return HttpResponse.json(
            { id: 'o1', status: 'REFUND_PENDING', refundReason: 'CUSTOMER' },
            { status: 202 },
          )
        },
      ),
    )
    const onClose = vi.fn()
    renderWithProviders(<RefundSheet ws={workspace.id} orderId="o1" onClose={onClose} />)

    // No reason is asked.
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Receber os R$ 89,00 de volta' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(body).toBe('')
  })
})
