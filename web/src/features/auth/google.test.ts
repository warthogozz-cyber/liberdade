import { describe, expect, it, vi } from 'vitest'
import { createTestQueryClient } from '@/shared/testing'
import { startGoogleSignIn } from './google'

describe('startGoogleSignIn', () => {
  it('empties the cache, then goes to the Google sign-in of the API', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(['me'], { user: { displayName: 'Ana Teste' } })
    const assign = vi.fn<(url: string) => void>()

    await startGoogleSignIn(queryClient, assign)

    expect(queryClient.getQueryCache().getAll()).toEqual([])
    expect(assign).toHaveBeenCalledTimes(1)
    expect(assign).toHaveBeenCalledWith('http://localhost:8080/oauth2/authorization/google')
  })
})
