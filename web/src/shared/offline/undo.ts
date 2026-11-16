import { ApiError } from '@/shared/api'
import { withOutboxLock } from './lock'
import { cancelOutboxItem, discardOutboxItem, notifyOutboxChanged } from './outbox'

/** How "Desfazer" ended: taken back, left for later for lack of signal, or refused by the API. */
export type UndoResult = 'undone' | 'offline' | 'failed'

/**
 * Takes back a transaction just recorded, wherever it is (D15). Still queued and never tried, it
 * leaves the queue, even offline. Otherwise the server may hold it: offline, nothing changes and the
 * person deletes it in Lançamentos later; online, any item still queued leaves first, holding the
 * outbox lock so no send can follow the delete, then the API deletes it. A 404 means the server never
 * stored it, which is undone too.
 */
export async function undoRecorded({
  id,
  workspaceId,
  online,
  remove,
}: {
  id: string
  workspaceId: string
  online: boolean
  remove: (workspaceId: string, id: string) => Promise<void>
}): Promise<UndoResult> {
  if (await cancelOutboxItem(id)) return 'undone'
  if (!online) return 'offline'

  const discarded = await withOutboxLock({}, () => discardOutboxItem(id, { lockHeld: true }))
  // Announced once the lock is free, so the sync it wakes is not turned away as busy.
  if (discarded) notifyOutboxChanged()

  try {
    await remove(workspaceId, id)
    return 'undone'
  } catch (error) {
    if (!(error instanceof ApiError) || error.status === 0) return 'offline'
    return error.status === 404 ? 'undone' : 'failed'
  }
}
