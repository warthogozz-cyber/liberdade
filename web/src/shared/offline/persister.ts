import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister'
import { defaultShouldDehydrateQuery, dehydrate, type QueryClient } from '@tanstack/react-query'
import type {
  PersistedClient,
  PersistQueryClientOptions,
} from '@tanstack/react-query-persist-client'
import { CACHE_MAX_AGE } from '@/shared/api'
import { openOfflineDb, type OfflineDb } from './openOfflineDb'

const CACHE_KEY = 'liberdade-query-cache'

/**
 * Set while the last read could not even load the database. TanStack drops the saved cache when a
 * restore throws; the database module can load on the very next call (a flaky network, a deploy),
 * and that drop would then delete offline data no one ever read.
 */
let unreadAfterLoadFailure = false

/** Saves the query cache in the `kv` table, under the key `liberdade-query-cache`. */
export const persister = createAsyncStoragePersister({
  storage: {
    getItem: async (key) => {
      let offline: OfflineDb
      try {
        offline = await openOfflineDb()
      } catch (error) {
        unreadAfterLoadFailure = true
        throw error
      }
      unreadAfterLoadFailure = false
      return (await offline.db.kv.get(key))?.value ?? null
    },
    setItem: async (key, value) => {
      await (await openOfflineDb()).db.kv.put({ key, value })
    },
    // Only TanStack's restore calls this; sign-out uses forgetSavedQueryCache.
    removeItem: async (key) => {
      if (unreadAfterLoadFailure) return
      await (await openOfflineDb()).db.kv.delete(key)
    },
  },
  key: CACHE_KEY,
})

/** Deletes the saved query cache, whatever the last read did: sign-out must never leave it behind. */
export async function forgetSavedQueryCache(): Promise<void> {
  await (await openOfflineDb()).db.kv.delete(CACHE_KEY)
}

export const persistOptions: Omit<PersistQueryClientOptions, 'queryClient'> = {
  persister,
  maxAge: CACHE_MAX_AGE,
  // Raise it when a schema.ts change makes the saved cache unreadable.
  buster: '1',
  // Writes made offline wait in the outbox, not in the cache.
  dehydrateOptions: {
    shouldDehydrateMutation: () => false,
    // A query can opt out with `meta: { persist: false }`: a copy read to be edited must be fresh.
    shouldDehydrateQuery: (query) =>
      defaultShouldDehydrateQuery(query) && query.meta?.persist !== false,
  },
}

/**
 * Saves the query cache to the device at once, past the persister's 1 s throttle, for a change a
 * reload must find (I1): the family just created, or its data just loaded for offline use. It never
 * rejects: if the save fails, the throttled save still follows.
 */
export async function saveQueryCacheNow(queryClient: QueryClient): Promise<void> {
  const client: PersistedClient = {
    timestamp: Date.now(),
    buster: persistOptions.buster ?? '',
    clientState: dehydrate(queryClient, persistOptions.dehydrateOptions),
  }
  try {
    const { db } = await openOfflineDb()
    await db.kv.put({ key: CACHE_KEY, value: JSON.stringify(client) })
  } catch {
    // The throttled save writes the same cache a moment later.
  }
}
