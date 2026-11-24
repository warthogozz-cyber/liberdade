import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { mockState, seedMockFamily } from './data'

async function get(path: string) {
  const response = await fetch(`${API_URL}${path}`)
  return { status: response.status, body: (await response.json()) as Record<string, unknown> }
}

describe('indicator mock handlers', () => {
  it('offIs403', async () => {
    const workspace = seedMockFamily()

    const response = await get(`/v1/workspaces/${workspace.id}/indicators`)

    expect(response.status).toBe(403)
    expect(response.body).toMatchObject({ code: 'module.disabled' })
  })

  it('onReturnsFive', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { indicators: true }

    const response = await get(`/v1/workspaces/${workspace.id}/indicators`)

    expect(response.status).toBe(200)
    const indicators = response.body.indicators as { code: string }[]
    expect(indicators.map((indicator) => indicator.code)).toEqual([
      'SELIC_TARGET',
      'CDI',
      'IPCA_MONTH',
      'IPCA_12M',
      'USD_PTAX_SELL',
    ])
  })
})
