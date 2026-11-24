import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelOutboxItem,
  clearOutboxForUser,
  countOutboxForUser,
  db,
  discardOutboxItem,
  enqueueTransaction,
  pendingTotals,
  replaceOutboxItem,
} from '@/shared/offline'
import { isLocalChange, resetLocalChanges } from '@/shared/live/localChanges'
import { outboxItem as item, outboxPayload as payload } from '@/shared/testing'

/** Undone after each test, so a failed assertion leaves no listener behind. */
const removals: (() => void)[] = []

function listenForChanges() {
  const listener = vi.fn()
  window.addEventListener('liberdade:outbox', listener)
  removals.push(() => window.removeEventListener('liberdade:outbox', listener))
  return listener
}

afterEach(() => {
  resetLocalChanges()
  for (const remove of removals.splice(0)) remove()
  vi.restoreAllMocks()
})

describe('enqueueTransaction', () => {
  it('stores a pending item due after the send delay and announces it once', async () => {
    const listener = listenForChanges()

    const queued = await enqueueTransaction(
      { userId: 'a', workspaceId: 'ws1', payload: payload('t1') },
      1000,
    )

    expect(queued).toEqual(item('t1'))
    expect(await db.outbox.get('t1')).toEqual(item('t1'))
    expect(listener).toHaveBeenCalledTimes(1)
  })
})

describe('enqueueMarksLocalChange', () => {
  it('marks the new transaction as saved on this device', async () => {
    await enqueueTransaction({ userId: 'a', workspaceId: 'ws1', payload: payload('t9') })
    expect(isLocalChange('t9')).toBe(true)
  })
})

describe('cancelOutboxItem', () => {
  it('is true while the item is queued and false once it is gone', async () => {
    await db.outbox.add(item('t1'))
    const listener = listenForChanges()

    expect(await cancelOutboxItem('t1')).toBe(true)
    expect(await cancelOutboxItem('t1')).toBe(false)

    expect(await db.outbox.count()).toBe(0)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('keeps an item whose send was already tried, since the server may have stored it', async () => {
    await db.outbox.add(item('t1', { attempts: 1 }))
    const listener = listenForChanges()

    expect(await cancelOutboxItem('t1')).toBe(false)

    expect(await db.outbox.toArray()).toEqual([item('t1', { attempts: 1 })])
    expect(listener).not.toHaveBeenCalled()
  })

  it('keeps an item that needs attention', async () => {
    await db.outbox.add(item('t1', { status: 'needs-attention' }))

    expect(await cancelOutboxItem('t1')).toBe(false)

    expect(await db.outbox.toArray()).toEqual([item('t1', { status: 'needs-attention' })])
  })
})

describe('discardOutboxItem', () => {
  it('removes an item that needs attention and announces it', async () => {
    await db.outbox.add(item('t1', { status: 'needs-attention', problem: { code: 'x' } }))
    const listener = listenForChanges()

    await discardOutboxItem('t1')

    expect(await db.outbox.count()).toBe(0)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('does nothing when the item is already gone', async () => {
    await db.outbox.add(item('other', { status: 'needs-attention' }))
    const listener = listenForChanges()

    await discardOutboxItem('t1')

    expect(await db.outbox.toArray()).toEqual([item('other', { status: 'needs-attention' })])
    expect(listener).not.toHaveBeenCalled()
  })

  it('refuses a pending item, which the sync may be sending, and leaves it', async () => {
    await db.outbox.add(item('t1'))
    const listener = listenForChanges()

    await expect(discardOutboxItem('t1')).rejects.toThrow()

    expect(await db.outbox.toArray()).toEqual([item('t1')])
    expect(listener).not.toHaveBeenCalled()
  })

  it('takes a pending item when the caller holds the lock, and leaves announcing to it', async () => {
    await db.outbox.add(item('t1', { attempts: 1 }))
    const listener = listenForChanges()

    expect(await discardOutboxItem('t1', { lockHeld: true })).toBe(true)
    expect(await discardOutboxItem('t1', { lockHeld: true })).toBe(false)

    expect(await db.outbox.count()).toBe(0)
    expect(listener).not.toHaveBeenCalled()
  })
})

describe('replaceOutboxItem', () => {
  it('swaps the item for a new id that keeps its place in the queue', async () => {
    await db.outbox.add(
      item('old', {
        createdAt: 500,
        sendAfter: 9000,
        attempts: 3,
        status: 'needs-attention',
        problem: { code: 'transaction.invalid-id', detail: 'Id inválido.' },
      }),
    )
    const listener = listenForChanges()

    const fixed = await replaceOutboxItem('old', payload('new', { occurredOn: '2026-11-02' }), 2000)

    const expected = item('new', {
      payload: payload('new', { occurredOn: '2026-11-02' }),
      createdAt: 500,
      sendAfter: 8000,
    })
    expect(fixed).toStrictEqual(expected)
    expect(await db.outbox.get('old')).toBeUndefined()
    expect(await db.outbox.toArray()).toEqual([expected])
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('rejects, and changes nothing, when the old item is gone', async () => {
    await db.outbox.add(item('other'))

    await expect(replaceOutboxItem('old', payload('new'))).rejects.toThrow()

    expect(await db.outbox.toArray()).toEqual([item('other')])
  })

  it('refuses a pending item, which the sync may be sending, and changes nothing', async () => {
    await db.outbox.add(item('old'))
    const listener = listenForChanges()

    await expect(replaceOutboxItem('old', payload('new'))).rejects.toThrow()

    expect(await db.outbox.toArray()).toEqual([item('old')])
    expect(listener).not.toHaveBeenCalled()
  })
})

describe('clearOutboxForUser and countOutboxForUser', () => {
  it('clears only that user’s items', async () => {
    await db.outbox.bulkAdd([
      item('a1'),
      item('a2', { createdAt: 2000, status: 'needs-attention' }),
      item('b1', { userId: 'b' }),
    ])
    const listener = listenForChanges()

    await clearOutboxForUser('a')

    expect(await db.outbox.toArray()).toEqual([item('b1', { userId: 'b' })])
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('counts the pending items and the ones that need attention', async () => {
    await db.outbox.bulkAdd([
      item('a1'),
      item('a2', { createdAt: 2000, status: 'needs-attention' }),
      item('b1', { userId: 'b' }),
    ])

    expect(await countOutboxForUser('a')).toBe(2)
  })
})

describe('pendingTotals', () => {
  const brl = (amount: string) => ({ amount, currency: 'BRL' })

  it('pendingTotalsByKind', () => {
    const items = [
      item('t1', { payload: payload('t1', { kind: 'INCOME', amount: brl('50.00') }) }),
      item('t2', { payload: payload('t2', { amount: brl('20.00') }) }),
      item('t3', {
        payload: payload('t3', {
          kind: 'TRANSFER',
          amount: brl('30.00'),
          categoryId: null,
          toAccountId: 'other-account',
        }),
      }),
    ]

    expect(pendingTotals(items, '2026-11')).toEqual({
      monthIncome: 5000n,
      monthExpenses: 2000n,
      balanceDelta: 3000n,
    })
  })

  it('counts another month only in the balance', () => {
    const items = [
      item('t1', { payload: payload('t1', { occurredOn: '2026-11-03' }) }),
      item('t2', { payload: payload('t2', { amount: brl('5.00'), occurredOn: '2026-10-31' }) }),
      item('t3', {
        payload: payload('t3', { kind: 'INCOME', amount: brl('1.00'), occurredOn: '2026-12-01' }),
      }),
    ]

    expect(pendingTotals(items, '2026-11')).toEqual({
      monthIncome: 0n,
      monthExpenses: 1234n,
      balanceDelta: -1634n,
    })
  })

  it('needsAttentionDoesNotCount', () => {
    const items = [
      item('t1', { status: 'needs-attention' }),
      item('t2', { status: 'needs-attention', payload: payload('t2', { kind: 'INCOME' }) }),
    ]

    expect(pendingTotals(items, '2026-11')).toEqual({
      monthIncome: 0n,
      monthExpenses: 0n,
      balanceDelta: 0n,
    })
  })
})
