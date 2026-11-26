import { waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderHookWithProviders } from '@/shared/testing'
import { marketsKeys, useMarketHome } from './markets'

describe('market hooks', () => {
  it('homeIsNotPersisted', async () => {
    const workspace = seedMockFamily()

    const home = renderHookWithProviders(() => useMarketHome(workspace.id))

    await waitFor(() => expect(home.result.current.fetchStatus).not.toBe('idle'))
    const query = home.queryClient
      .getQueryCache()
      .find({ queryKey: marketsKeys.home(workspace.id) })
    expect(query?.meta?.persist).toBe(false)
  })
})
