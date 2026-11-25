import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { db } from '@/shared/offline/db'
import { undoRecorded } from '@/shared/offline/undo'
import { outboxItem as item } from '@/shared/testing'

afterEach(() => {
  vi.restoreAllMocks()
})

function undo(online: boolean, remove = vi.fn(() => Promise.resolve())) {
  return { remove, result: undoRecorded({ id: 't1', workspaceId: 'ws1', online, remove }) }
}

describe('undoRecorded', () => {
  it('cancelsWhileQueued', async () => {
    await db.outbox.add(item('t1'))

    const { remove, result } = undo(true)

    expect(await result).toBe('undone')
    expect(remove).not.toHaveBeenCalled()
    expect(await db.outbox.count()).toBe(0)
  })

  it('cancels while queued even offline', async () => {
    await db.outbox.add(item('t1'))

    const { remove, result } = undo(false)

    expect(await result).toBe('undone')
    expect(remove).not.toHaveBeenCalled()
  })

  it('deletesWhenAlreadySent', async () => {
    const { remove, result } = undo(true)

    expect(await result).toBe('undone')
    expect(remove).toHaveBeenCalledTimes(1)
    expect(remove).toHaveBeenCalledWith('ws1', 't1')
  })

  it('notFoundCountsAsUndone', async () => {
    const { result } = undo(
      true,
      vi.fn(() => Promise.reject(new ApiError(404, 'transaction.not-found'))),
    )

    expect(await result).toBe('undone')
  })

  it('offlineAfterSend', async () => {
    const { remove, result } = undo(false)

    expect(await result).toBe('offline')
    expect(remove).not.toHaveBeenCalled()
  })

  it('offlineMidSendKeepsItem', async () => {
    await db.outbox.add(item('t1', { attempts: 1 }))

    const { remove, result } = undo(false)

    expect(await result).toBe('offline')
    expect(remove).not.toHaveBeenCalled()
    expect(await db.outbox.toArray()).toEqual([item('t1', { attempts: 1 })])
  })

  it('onlineMidSendDiscardsThenDeletes', async () => {
    await db.outbox.add(item('t1', { attempts: 1 }))
    const remove = vi.fn(async () => {
      // The item is out of the queue before the API is asked, so no send can follow the delete.
      expect(await db.outbox.get('t1')).toBeUndefined()
    })

    const { result } = undo(true, remove)

    expect(await result).toBe('undone')
    expect(remove).toHaveBeenCalledTimes(1)
    expect(await db.outbox.count()).toBe(0)
  })

  it('discards an item that needs attention and deletes it too', async () => {
    await db.outbox.add(item('t1', { attempts: 1, status: 'needs-attention' }))

    const { remove, result } = undo(true)

    expect(await result).toBe('undone')
    expect(remove).toHaveBeenCalledTimes(1)
    expect(await db.outbox.count()).toBe(0)
  })

  it('says offline when the delete gets no answer', async () => {
    const { result } = undo(
      true,
      vi.fn(() => Promise.reject(new ApiError(0, 'network'))),
    )

    expect(await result).toBe('offline')
  })

  it('says it failed for any other refusal', async () => {
    const { result } = undo(
      true,
      vi.fn(() => Promise.reject(new ApiError(500, 'internal.error'))),
    )

    expect(await result).toBe('failed')
  })

  it('wakes the sync once the lock is free, after taking an item out', async () => {
    await db.outbox.add(item('t1', { attempts: 1 }))
    const listener = vi.fn()
    window.addEventListener('liberdade:outbox', listener)

    try {
      await undo(true).result
    } finally {
      window.removeEventListener('liberdade:outbox', listener)
    }

    expect(listener).toHaveBeenCalledTimes(1)
  })
})
