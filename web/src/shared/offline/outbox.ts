import { Dexie } from 'dexie'
import type { CreateTransactionRequest } from '@/shared/api'
import { parseCents } from '@/shared/money'
import { db, type OutboxItem } from './db'
import { withOutboxLock } from './lock'

/** How long a new item waits before it is sent, so "Desfazer" can still take it back (D15). */
export const SEND_DELAY_MS = 6000

/** Fired on `window` when this tab changes the queue, to wake the sync. */
export const OUTBOX_EVENT = 'liberdade:outbox'

function notify(): void {
  window.dispatchEvent(new Event(OUTBOX_EVENT))
}

/** The user's items, both pending and needing attention, in the order they were made. */
export function userItems(userId: string) {
  return db.outbox
    .where('[userId+createdAt]')
    .between([userId, Dexie.minKey], [userId, Dexie.maxKey])
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
  await db.outbox.add(item)
  notify()
  return item
}

/**
 * Takes an item back before it is sent. It waits for the outbox lock, so it never races a send in
 * flight (D34). Only a pending item never tried goes: once a send was attempted the server may have
 * stored it, and an item that needs attention is fixed or discarded instead. `false` means the item
 * stayed or had already left the queue.
 */
export async function cancelOutboxItem(id: string): Promise<boolean> {
  const deleted = await withOutboxLock({}, () =>
    db.transaction('rw', db.outbox, async () => {
      const item = await db.outbox.get(id)
      if (item?.status !== 'pending' || item.attempts !== 0) return false
      await db.outbox.delete(id)
      return true
    }),
  )
  // Announced once the lock is free, so the sync it wakes is not turned away as busy.
  if (deleted) notify()
  return deleted
}

/**
 * Drops an item the API refused. Only an item that needs attention can go: the sync never touches
 * those, so this needs no lock. An item already gone is left at that (a double tap).
 */
export async function discardOutboxItem(id: string): Promise<void> {
  const discarded = await db.transaction('rw', db.outbox, async () => {
    const item = await db.outbox.get(id)
    if (!item) return false
    if (item.status !== 'needs-attention') {
      throw new Error('Only an outbox item that needs attention can be discarded')
    }
    await db.outbox.delete(id)
    return true
  })
  if (discarded) notify()
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
  notify()
  return item
}

/** Empties the user's queue, both states, once sign-out went through (D19). */
export async function clearOutboxForUser(userId: string): Promise<void> {
  await userItems(userId).delete()
  notify()
}

/** How many of the user's items are still queued, both states (D19). */
export function countOutboxForUser(userId: string): Promise<number> {
  return userItems(userId).count()
}

/**
 * The pending expenses in cents (D25): `monthExpenses` only those dated in `month` (`YYYY-MM`),
 * `allExpenses` all of them. Items that need attention count for neither.
 */
export function pendingTotals(
  items: OutboxItem[],
  month: string,
): { monthExpenses: bigint; allExpenses: bigint } {
  let monthExpenses = 0n
  let allExpenses = 0n
  for (const { status, payload } of items) {
    if (status !== 'pending' || payload.kind !== 'EXPENSE') continue
    const cents = parseCents(payload.amount.amount)
    allExpenses += cents
    if (payload.occurredOn.startsWith(`${month}-`)) monthExpenses += cents
  }
  return { monthExpenses, allExpenses }
}
