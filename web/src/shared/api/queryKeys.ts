import type { CategoryKind, PaymentMethod, TransactionFilters } from './types'

/** Everything a family owns sits under this key, so one invalidation reaches all of it. */
function workspace(ws: string) {
  return ['ws', ws] as const
}

export const queryKeys = {
  me: ['me'] as const,
  documents: ['me', 'documents'] as const,
  passkeys: ['me', 'passkeys'] as const,
  workspace,
  accounts: (ws: string) => [...workspace(ws), 'accounts'] as const,
  categories: (ws: string, kind: CategoryKind) => [...workspace(ws), 'categories', kind] as const,
  /** Filters take part in the key only when one is set, so the unfiltered month keeps its own. */
  transactions: (ws: string, month: string, filters?: TransactionFilters) =>
    [
      ...workspace(ws),
      'transactions',
      month,
      ...(filters?.categoryId || filters?.accountId || filters?.tagId || filters?.paymentMethod
        ? [
            {
              categoryId: filters.categoryId ?? null,
              accountId: filters.accountId ?? null,
              tagId: filters.tagId ?? null,
              paymentMethod: filters.paymentMethod ?? null,
            },
          ]
        : []),
    ] as const,
  members: (ws: string) => [...workspace(ws), 'members'] as const,
  invitations: (ws: string) => [...workspace(ws), 'invitations'] as const,
  modules: (ws: string) => [...workspace(ws), 'modules'] as const,
  /** The payment method takes part in the key only when one is set. */
  dashboard: (ws: string, month: string, paymentMethod?: PaymentMethod) =>
    [...workspace(ws), 'dashboard', month, ...(paymentMethod ? [{ paymentMethod }] : [])] as const,
  tags: (ws: string) => [...workspace(ws), 'tags'] as const,
}
