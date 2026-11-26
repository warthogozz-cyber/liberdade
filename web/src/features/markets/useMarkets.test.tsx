import { waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderHookWithProviders } from '@/shared/testing'
import { useMarketSearch } from './useMarkets'

describe('useMarketSearch', () => {
  it('sendsTwoDecimalsForAPrecisePosition', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { markets: true }
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/markets/search`, async ({ request }) => {
        bodies.push(await request.clone().json())
        return HttpResponse.json({
          datasetDate: null,
          osm: [],
          family: [],
          left: [],
          unlocated: [],
        })
      }),
    )

    const { result } = renderHookWithProviders(() =>
      useMarketSearch(workspace.id, { lat: -10.123456, lon: -50.987654 }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(bodies).toEqual([{ lat: '-10.12', lon: '-50.99', radiusKm: 3 }])
  })

  it('makesNoRequestWithoutACenter', async () => {
    const workspace = seedMockFamily()
    const { result } = renderHookWithProviders(() => useMarketSearch(workspace.id, null))

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(result.current.fetchStatus).toBe('idle')
  })
})
