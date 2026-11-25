import { dehydrate, type Mutation } from '@tanstack/react-query'
import type { PersistedClient } from '@tanstack/react-query-persist-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { queryKeys, type MeView, type MeWorkspace } from '@/shared/api'
import { persistOptions, saveQueryCacheNow } from '@/shared/offline'
import { db } from '@/shared/offline/db'
import { createTestQueryClient } from '@/shared/testing'

afterEach(() => {
  vi.restoreAllMocks()
})

const workspace: MeWorkspace = {
  id: '0192f3a4-0000-7000-8000-000000000002',
  membershipId: '0192f3a4-0000-7000-8000-000000000003',
  name: 'Família Teste',
  currency: 'BRL',
  country: 'BR',
  locale: 'pt-BR',
  paymentMethods: ['PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH'],
  role: 'OWNER',
  timezone: 'America/Fortaleza',
  gettingStartedDismissedAt: null,
  hasTransactions: false,
  familyInvited: false,
  recoveredAt: null,
  deletionScheduledFor: null,
}

const me: MeView = {
  user: {
    id: '0192f3a4-0000-7000-8000-000000000001',
    displayName: 'Ana Teste',
    passkeyCreatedAt: null,
    deletionScheduledFor: null,
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

  it('saves the cache at once with saveQueryCacheNow, past the 1 s throttle (I1)', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.me, me)
    // A save a moment ago holds the next throttled one back for a second.
    await persistOptions.persister.persistClient({
      timestamp: Date.now(),
      buster: '1',
      clientState: dehydrate(queryClient),
    })
    const withFamily: MeView = { ...me, workspaces: [workspace] }
    queryClient.setQueryData(queryKeys.me, withFamily)

    await saveQueryCacheNow(queryClient)

    const restored = await persistOptions.persister.restoreClient()
    expect(restored?.buster).toBe('1')
    expect(restored?.clientState.queries[0]?.state.data).toEqual(withFamily)
  })

  it('leaves a failed saveQueryCacheNow to the throttled save, without throwing', async () => {
    vi.spyOn(db.kv, 'put').mockRejectedValueOnce(new Error('QuotaExceededError'))

    await expect(saveQueryCacheNow(createTestQueryClient())).resolves.toBeUndefined()
  })

  it('leavesOutAQueryThatOptsOutOfPersisting', async () => {
    const queryClient = createTestQueryClient()
    await queryClient.prefetchQuery({
      queryKey: queryKeys.me,
      queryFn: () => me,
      meta: { persist: false },
    })
    await queryClient.prefetchQuery({ queryKey: ['kept'], queryFn: () => 'kept' })

    const saved = dehydrate(queryClient, persistOptions.dehydrateOptions)

    expect(saved.queries.map((query) => query.queryKey)).toEqual([['kept']])
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
