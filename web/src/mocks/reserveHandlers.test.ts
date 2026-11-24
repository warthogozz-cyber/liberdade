import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { mockState, seedMockFamily } from './data'

const WRITE = { 'X-XSRF-TOKEN': 'mock-csrf', 'Content-Type': 'application/json' }

async function call(path: string, method = 'GET', body?: unknown) {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: method === 'GET' ? {} : WRITE,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  return {
    status: response.status,
    body: (text ? JSON.parse(text) : null) as Record<string, unknown>,
  }
}

describe('reserve mock handlers', () => {
  it('keepsTheTargetAndTheTypedCost', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], planning: true }
    const path = `/v1/workspaces/${workspace.id}/reserve`

    const initial = await call(path)
    expect(initial.body).toMatchObject({ costSource: 'NONE', targetMonths: 6, months: null })

    const typed = await call(path, 'PATCH', {
      manualMonthlyCost: { amount: '500.00', currency: 'BRL' },
    })
    expect(typed.body).toMatchObject({ costSource: 'MANUAL', targetMonths: 6 })
    expect(typed.body.missing).toEqual({ amount: '3000.00', currency: 'BRL' })

    const target = await call(path, 'PATCH', { targetMonths: 12 })
    expect(target.body).toMatchObject({ targetMonths: 12, costSource: 'MANUAL' })

    const cleared = await call(path, 'PATCH', { manualMonthlyCost: null })
    expect(cleared.body).toMatchObject({ costSource: 'NONE', manualMonthlyCost: null })

    expect((await call(path, 'PATCH', { targetMonths: 25 })).status).toBe(400)
    expect((await call(path, 'PATCH', {})).status).toBe(400)
  })
})
