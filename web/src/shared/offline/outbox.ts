import type { CreateTransactionRequest } from '@/shared/api'
import { markLocalChange } from '@/shared/live/localChanges'
import { parseCents } from '@/shared/money'
import { withOutboxLock } from './lock'
import { openOfflineDb } from './openOfflineDb'
import { notifyQueued } from './queuedSignal'
import type { OutboxItem } from './types'

/** How long a new item waits before it is sent, so "Desfazer" can still take it back (D15). */
export const SEND_DELAY_MS = 6000

/** Fired on `window` when this tab changes the queue, to wake the sync. */
export const OUTBOX_EVENT = 'liberdade:outbox'

/** Wakes the sync. Callers holding the outbox lock call it once they let go. */
export function notifyOutboxChanged(): void {
  window.dispatchEvent(new Event(OUTBOX_EVENT))
}

/** The user's items, both pending and needing attention, in the order they were made. */
export async function userItems(userId: string) {
  return (await openOfflineDb()).userCollection(userId)
}

/** Queues a new transaction, to be sent once `SEND_DELAY_MS` has passed. */
export async function enqueueTransaction(
  input: { userId: string; workspaceId: string; payload: CreateTransactionRequest },
  now = Date.now(),
): Promise<OutboxItem> {
  const item: OutboxItem = {
    id: input.payload.id,
    userId: input.userId,
    workspaceId: input.workspaceId,
    type: 'transaction.create',
    payload: input.payload,
    createdAt: now,
    sendAfter: now + SEND_DELAY_MS,
    attempts: 0,
    status: 'pending',
  }
  // Before the item can be sent, so the stream's notice of it is never taken for an arrival.
  markLocalChange(item.id)
  const { db } = await openOfflineDb()
  await db.outbox.add(item)
  notifyQueued()
  notifyOutboxChanged()
  return item
}

/**
 * Takes an item back before it is sent. It waits for the outbox lock, so it never races a send in
 * flight (D34). Only a pending item never tried goes: once a send was attempted the server may have
 * stored it, and an item that needs attention is fixed or discarded instead. `false` means the item
 * stayed or had already left the queue.
 */
export async function cancelOutboxItem(id: string): Promise<boolean> {
  const { db } = await openOfflineDb()
  const deleted = await withOutboxLock({}, () =>
    db.transaction('rw', db.outbox, async () => {
      const item = await db.outbox.get(id)
      if (item?.status !== 'pending' || item.attempts !== 0) return false
      await db.outbox.delete(id)
      return true
    }),
  )
  // Announced once the lock is free, so the sync it wakes is not turned away as busy.
  if (deleted) notifyOutboxChanged()
  return deleted
}

/**
 * Drops an item from the queue. Without the outbox lock, only an item the API refused can go: the
 * sync never touches those. With `lockHeld`, the caller holds the lock, so no send is in flight and
 * any item can go; announcing it is then left to the caller, once the lock is free. An item already
 * gone is left at that (a double tap). `true` means this call took the item out.
 */
export async function discardOutboxItem(
  id: string,
  { lockHeld = false }: { lockHeld?: boolean } = {},
): Promise<boolean> {
  const { db } = await openOfflineDb()
  const discarded = await db.transaction('rw', db.outbox, async () => {
    const item = await db.outbox.get(id)
    if (!item) return false
    if (!lockHeld && item.status !== 'needs-attention') {
      throw new Error('Only an outbox item that needs attention can be discarded')
    }
    await db.outbox.delete(id)
    return true
  })
  if (discarded && !lockHeld) notifyOutboxChanged()
  return discarded
}

/**
 * Swaps an item the API refused for its fixed version (D24). The new one has its own id but keeps
 * the old `createdAt`, so it keeps its place in the queue, and starts over as pending. Only an item
 * that needs attention can be replaced, for the same reason as `discardOutboxItem`.
 */
export async function replaceOutboxItem(
  oldId: string,
  payload: CreateTransactionRequest,
  now = Date.now(),
): Promise<OutboxItem> {
  const { db } = await openOfflineDb()
  const item = await db.transaction('rw', db.outbox, async () => {
    const old = await db.outbox.get(oldId)
    if (!old) throw new Error('The outbox item to replace is gone')
    if (old.status !== 'needs-attention') {
      throw new Error('Only an outbox item that needs attention can be replaced')
    }
    const fixed: OutboxItem = {
      id: payload.id,
      userId: old.userId,
      workspaceId: old.workspaceId,
      type: old.type,
      payload,
      createdAt: old.createdAt,
      sendAfter: now + SEND_DELAY_MS,
      attempts: 0,
      status: 'pending',
    }
    await db.outbox.delete(oldId)
    await db.outbox.add(fixed)
    return fixed
  })
  notifyOutboxChanged()
  return item
}

/** Empties the user's queue, both states, once sign-out went through (D19). */
export async function clearOutboxForUser(userId: string): Promise<void> {
  await (await userItems(userId)).delete()
  notifyOutboxChanged()
}

/** Lets the user's pending items go out now, not after their backoff, and wakes the sync. */
export async function sendPendingNow(userId: string): Promise<void> {
  await (
    await userItems(userId)
  )
    .filter((item) => item.status === 'pending')
    .modify({ sendAfter: 0 })
  notifyOutboxChanged()
}

/** How many of the user's items are still queued, both states (D19). */
export async function countOutboxForUser(userId: string): Promise<number> {
  return (await userItems(userId)).count()
}

/**
 * What the pending items add to the totals, in cents (D25). `monthIncome` and `monthExpenses` count
 * only items dated in `month` (`YYYY-MM`); `balanceDelta` counts every date: an income adds, an
 * expense takes away, and a transfer moves money between two active accounts, so it changes
 * nothing. Items that need attention count for none.
 */
export function pendingTotals(
  items: OutboxItem[],
  month: string,
): { monthIncome: bigint; monthExpenses: bigint; balanceDelta: bigint } {
  let monthIncome = 0n
  let monthExpenses = 0n
  let balanceDelta = 0n
  for (const { status, payload } of items) {
    if (status !== 'pending' || payload.kind === 'TRANSFER') continue
    const cents = parseCents(payload.amount.amount)
    const inMonth = payload.occurredOn.startsWith(`${month}-`)
    if (payload.kind === 'INCOME') {
      balanceDelta += cents
      if (inMonth) monthIncome += cents
    } else {
      balanceDelta -= cents
      if (inMonth) monthExpenses += cents
    }
  }
  return { monthIncome, monthExpenses, balanceDelta }
}
