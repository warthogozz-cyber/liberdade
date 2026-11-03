import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CreateTransactionRequest } from '@/shared/api'
import {
  cancelOutboxItem,
  clearOutboxForUser,
  countOutboxForUser,
  db,
  discardOutboxItem,
  enqueueTransaction,
  pendingTotals,
  replaceOutboxItem,
  type OutboxItem,
} from '@/shared/offline'

function payload(id: string, overrides: Partial<CreateTransactionRequest> = {}) {
  return {
    id,
    kind: 'EXPENSE',
    amount: { amount: '12.34', currency: 'BRL' },
    occurredOn: '2026-11-03',
    accountId: 'account',
    categoryId: 'category',
    ...overrides,
  } satisfies CreateTransactionRequest
}

function item(id: string, overrides: Partial<OutboxItem> = {}): OutboxItem {
  return {
    id,
    userId: 'a',
    workspaceId: 'ws1',
    type: 'transaction.create',
    payload: payload(id),
    createdAt: 1000,
    sendAfter: 7000,
    attempts: 0,
    status: 'pending',
    ...overrides,
  }
}

function listenForChanges() {
  const listener = vi.fn()
  window.addEventListener('liberdade:outbox', listener)
  return {
    listener,
    stop: () => window.removeEventListener('liberdade:outbox', listener),
  }
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('enqueueTransaction', () => {
  it('stores a pending item due after the send delay and announces it once', async () => {
    const { listener, stop } = listenForChanges()

    const queued = await enqueueTransaction(
      { userId: 'a', workspaceId: 'ws1', payload: payload('t1') },
      1000,
    )

    expect(queued).toEqual(item('t1'))
    expect(await db.outbox.get('t1')).toEqual(item('t1'))
    expect(listener).toHaveBeenCalledTimes(1)
    stop()
  })
})

describe('cancelOutboxItem', () => {
  it('is true while the item is queued and false once it is gone', async () => {
    await db.outbox.add(item('t1'))
    const { listener, stop } = listenForChanges()

    expect(await cancelOutboxItem('t1')).toBe(true)
    expect(await cancelOutboxItem('t1')).toBe(false)

    expect(await db.outbox.count()).toBe(0)
    expect(listener).toHaveBeenCalledTimes(1)
    stop()
  })
})

describe('discardOutboxItem', () => {
  it('removes the item and announces it', async () => {
    await db.outbox.add(item('t1', { status: 'needs-attention', problem: { code: 'x' } }))
    const { listener, stop } = listenForChanges()

    await discardOutboxItem('t1')

    expect(await db.outbox.count()).toBe(0)
    expect(listener).toHaveBeenCalledTimes(1)
    stop()
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
    const { listener, stop } = listenForChanges()

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
    stop()
  })

  it('rejects, and changes nothing, when the old item is gone', async () => {
    await db.outbox.add(item('other'))

    await expect(replaceOutboxItem('old', payload('new'))).rejects.toThrow()

    expect(await db.outbox.toArray()).toEqual([item('other')])
  })
})

describe('clearOutboxForUser and countOutboxForUser', () => {
  it('clears only that user’s items', async () => {
    await db.outbox.bulkAdd([
      item('a1'),
      item('a2', { createdAt: 2000, status: 'needs-attention' }),
      item('b1', { userId: 'b' }),
    ])
    const { listener, stop } = listenForChanges()

    await clearOutboxForUser('a')

    expect(await db.outbox.toArray()).toEqual([item('b1', { userId: 'b' })])
    expect(listener).toHaveBeenCalledTimes(1)
    stop()
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
  it('adds up the pending expenses, for the month and in all', () => {
    const items = [
      item('t1', { payload: payload('t1', { occurredOn: '2026-11-03' }) }),
      item('t2', {
        payload: payload('t2', {
          amount: { amount: '5.00', currency: 'BRL' },
          occurredOn: '2026-10-31',
        }),
      }),
      item('t3', {
        status: 'needs-attention',
        payload: payload('t3', {
          amount: { amount: '7.00', currency: 'BRL' },
          occurredOn: '2026-11-01',
        }),
      }),
    ]

    expect(pendingTotals(items, '2026-11')).toEqual({ monthExpenses: 1234n, allExpenses: 1734n })
  })

  it('leaves income out', () => {
    const items = [item('t1', { payload: payload('t1', { kind: 'INCOME' }) })]

    expect(pendingTotals(items, '2026-11')).toEqual({ monthExpenses: 0n, allExpenses: 0n })
  })
})
