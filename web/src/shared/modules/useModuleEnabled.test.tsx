import { waitFor } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderHookWithProviders } from '@/shared/testing'
import { useModuleEnabled } from './useModuleEnabled'

describe('useModuleEnabled', () => {
  it('followsApi', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    const planning = renderHookWithProviders(() => useModuleEnabled('planning'))

    expect(planning.result.current).toBe(false)
    await waitFor(() => expect(planning.result.current).toBe(true))

    mockState.modules[workspace.id] = { planning: false }
    const off = renderHookWithProviders(() => useModuleEnabled('planning'))
    await waitFor(() =>
      expect(off.queryClient.getQueryState(['ws', workspace.id, 'modules'])?.status).toBe(
        'success',
      ),
    )
    expect(off.result.current).toBe(false)
  })

  it('is false without a family and when the list cannot be loaded', async () => {
    const empty = renderHookWithProviders(() => useModuleEnabled('planning'))
    await waitFor(() => expect(empty.queryClient.getQueryState(['me'])?.status).toBe('success'))
    expect(empty.result.current).toBe(false)

    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    server.use(http.get(`${API_URL}/v1/workspaces/:ws/modules`, () => problem(500, 'server.error')))
    const failing = renderHookWithProviders(() => useModuleEnabled('planning'))
    await waitFor(() =>
      expect(failing.queryClient.getQueryState(['ws', workspace.id, 'modules'])?.status).toBe(
        'error',
      ),
    )
    expect(failing.result.current).toBe(false)
  })
})
