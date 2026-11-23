import { afterEach, describe, expect, it, vi } from 'vitest'
import { API_URL } from '@/shared/api'
import { mockState, seedMockFamily } from './data'

const WRITE = { 'X-XSRF-TOKEN': 'mock-csrf' }

afterEach(() => {
  vi.useRealTimers()
})

async function call(path: string, method = 'GET') {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: method === 'GET' ? {} : WRITE,
  })
  return { status: response.status, body: (await response.json()) as Record<string, unknown> }
}

function paidFamily() {
  const workspace = seedMockFamily()
  mockState.billing.plan = { key: 'FAMILY', endsAt: '2027-11-30T03:00:00Z' }
  mockState.billing.orders.push({
    id: 'paid-1',
    workspaceId: workspace.id,
    status: 'PAID',
    checkoutUrl: 'https://checkout.e2e.test/c/chk_fake_paid-1',
  })
  return workspace
}

describe('billing mock handlers', () => {
  it('refundDeadlineIsTheEndOfTheSeventhLocalDay', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    // 22:00 in São Paulo on 24/11: the last allowed day is 01/12, so the deadline is 00:00 on 02/12.
    vi.setSystemTime(new Date('2026-11-25T01:00:00Z'))
    const workspace = paidFamily()

    const { status, body } = await call(`/v1/workspaces/${workspace.id}/billing/plan`)

    expect(status).toBe(200)
    expect(body.refundDeadline).toBe('2026-12-02T03:00:00.000Z')
    expect(body.canRefund).toBe(true)
    expect(body.paidOrderId).toBe('paid-1')
  })

  it('aNewCheckoutCancelsTheOneStillWaiting', async () => {
    const workspace = seedMockFamily()

    const first = await call(`/v1/workspaces/${workspace.id}/billing/checkout`, 'POST')
    const second = await call(`/v1/workspaces/${workspace.id}/billing/checkout`, 'POST')

    expect(first.status).toBe(201)
    expect(second.status).toBe(201)
    expect(mockState.billing.orders.map((order) => order.status)).toEqual(['CANCELED', 'CREATED'])
  })

  it('aRefundEndsThePlanAtOnce', async () => {
    const workspace = paidFamily()

    const refund = await call(`/v1/workspaces/${workspace.id}/billing/orders/paid-1/refund`, 'POST')
    const plan = await call(`/v1/workspaces/${workspace.id}/billing/plan`)

    expect(refund.status).toBe(202)
    expect(refund.body.status).toBe('REFUND_PENDING')
    expect(plan.body.key).toBe('FREE')
    expect(plan.body.refundDeadline).toBeNull()
    expect((plan.body.openOrder as { status: string }).status).toBe('REFUND_PENDING')
  })

  it('aRunningPlanRefusesACheckout', async () => {
    const workspace = paidFamily()

    const { status, body } = await call(`/v1/workspaces/${workspace.id}/billing/checkout`, 'POST')

    expect(status).toBe(409)
    expect(body.code).toBe('billing.plan-active')
  })
})
