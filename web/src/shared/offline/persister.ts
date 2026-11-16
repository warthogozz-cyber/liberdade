import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister'
import { defaultShouldDehydrateQuery, dehydrate, type QueryClient } from '@tanstack/react-query'
import type {
  PersistedClient,
  PersistQueryClientOptions,
} from '@tanstack/react-query-persist-client'
import { CACHE_MAX_AGE } from '@/shared/api'
import { db } from './db'

const CACHE_KEY = 'liberdade-query-cache'

/** Saves the query cache in the `kv` table, under the key `liberdade-query-cache`. */
export const persister = createAsyncStoragePersister({
  storage: {
    getItem: async (key) => (await db.kv.get(key))?.value ?? null,
    setItem: async (key, value) => {
      await db.kv.put({ key, value })
    },
    removeItem: async (key) => {
      await db.kv.delete(key)
    },
  },
  key: CACHE_KEY,
})

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
    await db.kv.put({ key: CACHE_KEY, value: JSON.stringify(client) })
  } catch {
    // The throttled save writes the same cache a moment later.
  }
}
