import { useEffect, useMemo, useState } from 'react'
import { openOfflineDb } from './openOfflineDb'
import { pendingTotals } from './outbox'
import type { OutboxItem } from './types'

const NO_ITEMS: OutboxItem[] = []

interface Snapshot {
  userId: string
  workspaceId: string | undefined
  items: OutboxItem[]
}

/**
 * The user's queued items in the family, in the order they were made, kept current across tabs.
 * `undefined` while loading. Without a user it is empty: someone else's queue never shows (RF2).
 */
export function useOutbox(
  userId: string | undefined,
  workspaceId: string | undefined,
): OutboxItem[] | undefined {
  const [snapshot, setSnapshot] = useState<Snapshot>()
  useEffect(() => {
    if (userId === undefined) return
    let stopped = false
    let unsubscribe: (() => void) | undefined
    openOfflineDb().then(
      ({ userCollection, watch }) => {
        // The user or the family may have changed while the database loaded.
        if (stopped) return
        unsubscribe = watch(
          async () => ({
            userId,
            workspaceId,
            items: await userCollection(userId)
              .filter((item) => item.workspaceId === workspaceId)
              .toArray(),
          }),
          setSnapshot,
        )
      },
      () => {
        // The queue stays loading; the next mount loads the database again.
      },
    )
    return () => {
      stopped = true
      unsubscribe?.()
    }
  }, [userId, workspaceId])
  if (userId === undefined) return NO_ITEMS
  // The last snapshot stays until the new query answers; never show it for another user or family.
  if (snapshot?.userId !== userId || snapshot.workspaceId !== workspaceId) return undefined
  return snapshot.items
}

/** What the user's pending items add to the totals, in cents (D25); zero while the queue loads. */
export function usePendingTotals(
  userId: string | undefined,
  workspaceId: string | undefined,
  month: string,
): ReturnType<typeof pendingTotals> {
  const items = useOutbox(userId, workspaceId)
  return useMemo(() => pendingTotals(items ?? NO_ITEMS, month), [items, month])
}
