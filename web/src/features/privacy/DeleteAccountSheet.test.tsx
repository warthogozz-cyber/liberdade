import { cleanup, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { DeleteAccountSheet } from './DeleteAccountSheet'

afterEach(cleanup)

const REMINDER = /Sua família é Fundadora há menos de 7 dias/

let planRequests = 0

function plan(ws: string, canRefund: boolean) {
  server.use(
    http.get(`${API_URL}/v1/workspaces/${ws}/billing/plan`, () => {
      planRequests += 1
      return HttpResponse.json({
        key: canRefund ? 'FAMILY' : 'FREE',
        founder: canRefund,
        endsAt: null,
        theme: 'TEAL',
        presaleOpen: true,
        canPurchase: !canRefund,
        canRefund,
        refundDeadline: null,
        paidOrderId: canRefund ? 'o1' : null,
        openOrder: null,
      })
    }),
  )
}

describe('DeleteAccountSheet', () => {
  it('remindsRefundWithinSevenDays', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, true)
    const view = renderWithProviders(<DeleteAccountSheet onClose={() => {}} />)

    expect(await screen.findByText(REMINDER)).toBeInTheDocument()
    view.unmount()

    plan(workspace.id, false)
    planRequests = 0
    renderWithProviders(<DeleteAccountSheet onClose={() => {}} />)
    expect(await screen.findByRole('button', { name: 'Excluir minha conta' })).toBeInTheDocument()
    // The plan arrived (and said no) before its absence is asserted.
    await waitFor(() => expect(planRequests).toBeGreaterThan(0))
    await waitFor(() => expect(screen.queryByText(REMINDER)).not.toBeInTheDocument())
    expect(screen.queryByText(REMINDER)).not.toBeInTheDocument()
  })
})
