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
    toAccountId: request.toAccountId ?? null,
    categoryId: request.categoryId ?? null,
    description: request.description ?? null,
    paymentMethod: request.paymentMethod ?? null,
    tags: (request.tags ?? []).filter((name) => name !== null),
    status: 'CONFIRMED',
    scheduledOn: null,
    recurringRuleId: null,
    createdAt,
    memberId: 'member',
    version: 0,
  }
}

/** A planned transaction a recurring rule generated: not counted until it is confirmed. */
export function plannedView(
  request: CreateTransactionRequest,
  scheduledOn: string,
  overrides: Partial<TransactionView> = {},
): TransactionView {
  return {
    ...transactionView({ ...request, occurredOn: scheduledOn }),
    status: 'PLANNED',
    scheduledOn,
    recurringRuleId: '0192f3a4-0000-7000-8000-0000000000c1',
    ...overrides,
  }
}
