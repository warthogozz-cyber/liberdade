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
