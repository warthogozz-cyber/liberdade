import { useLiveQuery } from 'dexie-react-hooks'
import { useMemo } from 'react'
import type { OutboxItem } from './db'
import { pendingTotals, userItems } from './outbox'

const NO_ITEMS: OutboxItem[] = []

/**
 * The user's queued items in the family, in the order they were made, kept current across tabs.
 * `undefined` while loading. Without a user it is empty: someone else's queue never shows (RF2).
 */
export function useOutbox(
  userId: string | undefined,
  workspaceId: string | undefined,
): OutboxItem[] | undefined {
  const snapshot = useLiveQuery(
    async () =>
      userId === undefined
        ? undefined
        : {
            userId,
            workspaceId,
            items: await userItems(userId)
              .filter((item) => item.workspaceId === workspaceId)
              .toArray(),
          },
    [userId, workspaceId],
  )
  if (userId === undefined) return NO_ITEMS
  // useLiveQuery keeps the last result until the new query answers; never show it for another user.
  if (snapshot?.userId !== userId || snapshot.workspaceId !== workspaceId) return undefined
  return snapshot.items
}

/** The user's pending expenses in cents (D25); zero while the queue loads. */
export function usePendingTotals(
  userId: string | undefined,
  workspaceId: string | undefined,
  month: string,
): { monthExpenses: bigint; allExpenses: bigint } {
  const items = useOutbox(userId, workspaceId)
  return useMemo(() => pendingTotals(items ?? NO_ITEMS, month), [items, month])
}
