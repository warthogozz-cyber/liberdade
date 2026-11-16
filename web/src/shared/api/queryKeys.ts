import type { CategoryKind } from './types'

/** Everything a family owns sits under this key, so one invalidation reaches all of it. */
function workspace(ws: string) {
  return ['ws', ws] as const
}

export const queryKeys = {
  me: ['me'] as const,
  workspace,
  accounts: (ws: string) => [...workspace(ws), 'accounts'] as const,
  categories: (ws: string, kind: CategoryKind) => [...workspace(ws), 'categories', kind] as const,
  transactions: (ws: string, month: string) => [...workspace(ws), 'transactions', month] as const,
  dashboard: (ws: string, month: string) => [...workspace(ws), 'dashboard', month] as const,
}
