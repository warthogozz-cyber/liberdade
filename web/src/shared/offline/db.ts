// The only module that loads Dexie. Reach it through `openOfflineDb()`, never with a static import,
// so Dexie stays out of the first load (the guard in scripts/check-chunks.mjs fails the build).
import { Dexie, liveQuery, type EntityTable } from 'dexie'
import type { KvRow, OutboxItem } from './types'

/** The device's database: the outbox, kept per user, and key-value rows. */
export const db = new Dexie('liberdade') as Dexie & {
  outbox: EntityTable<OutboxItem, 'id'>
  kv: EntityTable<KvRow, 'key'>
}

db.version(1).stores({
  outbox: 'id, [userId+createdAt], workspaceId',
  kv: 'key',
})

/** The user's items, both pending and needing attention, in the order they were made. */
export function userCollection(userId: string) {
  return db.outbox
    .where('[userId+createdAt]')
    .between([userId, Dexie.minKey], [userId, Dexie.maxKey])
}

/**
 * Calls `onValue` with the query's answer now and again whenever the data it read changes, in this
 * tab or another. A failed run is left out, keeping the last answer. Returns the unsubscribe.
 */
export function watch<T>(query: () => Promise<T>, onValue: (value: T) => void): () => void {
  const subscription = liveQuery(query).subscribe({
    next: onValue,
    error: () => {
      // The next change runs the query again; the last answer stays meanwhile.
    },
  })
  return () => {
    subscription.unsubscribe()
  }
}
