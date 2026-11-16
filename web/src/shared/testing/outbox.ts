import type { CreateTransactionRequest, TransactionView } from '@/shared/api'
import type { OutboxItem } from '@/shared/offline'

/** A request for an expense of R$ 12,34 on 2026-11-03, with `id`. */
export function outboxPayload(id: string, overrides: Partial<CreateTransactionRequest> = {}) {
  return {
    id,
    kind: 'EXPENSE',
    amount: { amount: '12.34', currency: 'BRL' },
    occurredOn: '2026-11-03',
    accountId: 'account',
    categoryId: 'category',
    ...overrides,
  } satisfies CreateTransactionRequest
}

/** A pending item of user `a` in family `ws1`, made at 1000 and due at 7000. */
export function outboxItem(id: string, overrides: Partial<OutboxItem> = {}): OutboxItem {
  return {
    id,
    userId: 'a',
    workspaceId: 'ws1',
    type: 'transaction.create',
    payload: outboxPayload(id),
    createdAt: 1000,
    sendAfter: 7000,
    attempts: 0,
    status: 'pending',
    ...overrides,
  }
}

/** The transaction the API answers with once it stores `request`. */
export function transactionView(
  request: CreateTransactionRequest,
  createdAt = '2026-11-03T12:00:00Z',
): TransactionView {
  return {
    ...request,
    description: request.description ?? null,
    createdAt,
    memberId: 'member',
  }
}
