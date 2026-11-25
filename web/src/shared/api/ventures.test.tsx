import { waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { renderHookWithProviders } from '@/shared/testing'
import { useVentures, venturesKeys } from './ventures'

describe('venture hooks', () => {
  it('listIsPersisted', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)

    const list = renderHookWithProviders(() => useVentures(workspace.id))

    await waitFor(() => expect(list.result.current.data?.ventures).toHaveLength(1))
    const query = list.queryClient
      .getQueryCache()
      .find({ queryKey: venturesKeys.list(workspace.id) })
    expect(query?.meta?.persist).toBe(true)
  })

  it('keepsEveryKeyUnderTheFamilyKey', () => {
    expect(venturesKeys.detail('w', 'v').slice(0, venturesKeys.all('w').length)).toEqual(
      venturesKeys.all('w'),
    )
    expect(venturesKeys.templates('w')[0]).toBe('ws')
  })
})
