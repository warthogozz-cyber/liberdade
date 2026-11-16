import type { TransactionKind } from '@/shared/api'

/** The id of a kind's tab, so the panel can be named after the chosen one. */
export function kindTabId(panelId: string, kind: TransactionKind): string {
  return `${panelId}-tab-${kind.toLowerCase()}`
}
