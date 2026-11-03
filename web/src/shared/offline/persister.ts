import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister'
import type { PersistQueryClientOptions } from '@tanstack/react-query-persist-client'
import { CACHE_MAX_AGE } from '@/shared/api'
import { db } from './db'

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
  key: 'liberdade-query-cache',
})

export const persistOptions: Omit<PersistQueryClientOptions, 'queryClient'> = {
  persister,
  maxAge: CACHE_MAX_AGE,
  // Raise it when a schema.ts change makes the saved cache unreadable.
  buster: '1',
  // Writes made offline wait in the outbox, not in the cache.
  dehydrateOptions: { shouldDehydrateMutation: () => false },
}
