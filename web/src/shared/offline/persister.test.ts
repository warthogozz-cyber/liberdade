import { dehydrate, type Mutation } from '@tanstack/react-query'
import type { PersistedClient } from '@tanstack/react-query-persist-client'
import { describe, expect, it } from 'vitest'
import { queryKeys, type MeView } from '@/shared/api'
import { db, persistOptions } from '@/shared/offline'
import { createTestQueryClient } from '@/shared/testing'

const me: MeView = {
  user: {
    id: '0192f3a4-0000-7000-8000-000000000001',
    displayName: 'Ana Teste',
    passkeyCreatedAt: null,
  },
  workspaces: [],
  suggestedWorkspaceName: 'Família Teste',
}

describe('persistOptions', () => {
  it('saves the query cache in the kv table and restores it unchanged', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.me, me)
    const client: PersistedClient = {
      timestamp: Date.now(),
      buster: '1',
      clientState: dehydrate(queryClient),
    }

    await persistOptions.persister.persistClient(client)

    expect(await persistOptions.persister.restoreClient()).toEqual(client)
    expect(await db.kv.get('liberdade-query-cache')).toBeDefined()
  })

  it('restores nothing when nothing was saved', async () => {
    expect(await persistOptions.persister.restoreClient()).toBeUndefined()
  })

  it('keeps the cache for 30 days under buster 1 and never saves mutations', () => {
    const paused = { state: { isPaused: true } } as Mutation

    expect(persistOptions.maxAge).toBe(30 * 24 * 60 * 60 * 1000)
    expect(persistOptions.buster).toBe('1')
    expect(persistOptions.dehydrateOptions?.shouldDehydrateMutation?.(paused)).toBe(false)
  })
})
