import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { mockState, seedMockFamily } from './data'

async function get(path: string) {
  const response = await fetch(`${API_URL}${path}`)
  return { status: response.status, body: (await response.json()) as Record<string, unknown> }
}

describe('projection mock handlers', () => {
  it('projectsFourMonthEndsAndSplitsTheMonth', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], planning: true }
    const base = `/v1/workspaces/${workspace.id}`

    const projection = await get(`${base}/projection`)
    expect(projection.status).toBe(200)
    expect(projection.body).toMatchObject({ hasHistory: false, averageSpending: null })
    expect(projection.body.points).toHaveLength(4)

    const split = await get(`${base}/spending-split?month=2026-11`)
    expect(split.status).toBe(200)
    expect(split.body).toMatchObject({
      month: '2026-11',
      fixed: { amount: '0.00', currency: 'BRL' },
      variable: { amount: '0.00', currency: 'BRL' },
    })

    expect((await get(`${base}/spending-split`)).status).toBe(400)
    expect((await get(`${base}/spending-split?month=2026-13`)).status).toBe(400)
  })

  it('needsThePlanningModule', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], planning: false }

    const result = await get(`/v1/workspaces/${workspace.id}/projection`)

    expect(result.status).toBe(403)
    expect(result.body.code).toBe('module.disabled')
  })
})
