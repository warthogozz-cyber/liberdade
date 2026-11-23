import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { mockState, seedMockFamily } from './data'

const WRITE = { 'X-XSRF-TOKEN': 'mock-csrf', 'Content-Type': 'application/json' }
const money = (amount: string) => ({ amount, currency: 'BRL' })

let sequence = 0
/** A version 7 identifier, as the API demands. */
const nextId = () => `0192f3a4-0000-7000-8000-${String(++sequence).padStart(12, '0')}`

async function post(workspaceId: string, body: Record<string, unknown>) {
  const id = nextId()
  const response = await fetch(`${API_URL}/v1/workspaces/${workspaceId}/transactions`, {
    method: 'POST',
    headers: { ...WRITE, 'Idempotency-Key': id },
    body: JSON.stringify({ id, occurredOn: '2026-11-03', ...body }),
  })
  return { status: response.status, body: (await response.json()) as Record<string, unknown> }
}

describe('transaction mock handlers', () => {
  it('lists only the transactions of the asked payment method', async () => {
    const workspace = seedMockFamily()
    const [wallet] = mockState.accounts
    const categoryId = mockState.categories.find((category) => category.kind === 'EXPENSE')!.id
    const base = { kind: 'EXPENSE', amount: money('10.00'), accountId: wallet.id, categoryId }
    await post(workspace.id, { ...base, paymentMethod: 'PIX' })
    await post(workspace.id, { ...base, paymentMethod: 'CASH' })

    const response = await fetch(
      `${API_URL}/v1/workspaces/${workspace.id}/transactions?month=2026-11&paymentMethod=PIX`,
    )
    const items = (await response.json()) as { paymentMethod: string | null }[]

    expect(items.map((item) => item.paymentMethod)).toEqual(['PIX'])
  })

  it('refuses a payment method on a transfer', async () => {
    const workspace = seedMockFamily()
    const [wallet] = mockState.accounts
    mockState.accounts.push({ ...wallet, id: crypto.randomUUID(), name: 'Banco' })

    const { status, body } = await post(workspace.id, {
      kind: 'TRANSFER',
      amount: money('10.00'),
      accountId: wallet.id,
      toAccountId: mockState.accounts[1].id,
      paymentMethod: 'PIX',
    })

    expect(status).toBe(422)
    expect(body.code).toBe('transaction.payment-method-transfer')
  })
})
