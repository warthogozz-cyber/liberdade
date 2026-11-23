import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { DeleteAccountSheet } from './DeleteAccountSheet'

afterEach(cleanup)

const REMINDER = /Sua família é Fundadora há menos de 7 dias/

function plan(ws: string, canRefund: boolean) {
  server.use(
    http.get(`${API_URL}/v1/workspaces/${ws}/billing/plan`, () =>
      HttpResponse.json({
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
      }),
    ),
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
    renderWithProviders(<DeleteAccountSheet onClose={() => {}} />)
    expect(await screen.findByRole('button', { name: 'Excluir minha conta' })).toBeInTheDocument()
    // Give the plan time to arrive before asserting its absence.
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByText(REMINDER)).not.toBeInTheDocument()
  })
})
