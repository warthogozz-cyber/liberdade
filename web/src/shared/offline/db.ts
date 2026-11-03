import { Dexie, type EntityTable } from 'dexie'
import type { CreateTransactionRequest } from '@/shared/api'

/** A write waiting to reach the API. Dates are epoch milliseconds. */
export interface OutboxItem {
  id: string
  userId: string
  workspaceId: string
  type: 'transaction.create'
  payload: CreateTransactionRequest
  createdAt: number
  sendAfter: number
  attempts: number
  status: 'pending' | 'needs-attention'
  problem?: { code: string; detail?: string }
}

/** A stored text value, such as the saved query cache. */
export interface KvRow {
  key: string
  value: string
}

/** The device's database: the outbox, kept per user, and key-value rows. */
export const db = new Dexie('liberdade') as Dexie & {
  outbox: EntityTable<OutboxItem, 'id'>
  kv: EntityTable<KvRow, 'key'>
}

db.version(1).stores({
  outbox: 'id, [userId+createdAt], workspaceId',
  kv: 'key',
})
