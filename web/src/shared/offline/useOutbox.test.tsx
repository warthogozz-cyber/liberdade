import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { enqueueTransaction, useOutbox, usePendingTotals } from '@/shared/offline'
import { db } from '@/shared/offline/db'
import { outboxItem as item, outboxPayload as payload } from '@/shared/testing'

describe('useOutbox', () => {
  it('shows an item as soon as it is queued', async () => {
    const { result, unmount } = renderHook(() => useOutbox('a', 'ws1'))
    await waitFor(() => expect(result.current).toEqual([]))

    await act(() =>
      enqueueTransaction({ userId: 'a', workspaceId: 'ws1', payload: payload('t1') }, 1000),
    )

    await waitFor(() => expect(result.current).toEqual([item('t1')]))
    unmount()
  })

  it('lists the user’s items in the family, in the order they were made', async () => {
    await db.outbox.bulkAdd([
      item('z', { createdAt: 1000 }),
      item('y', { createdAt: 3000 }),
      item('x', { createdAt: 2000, status: 'needs-attention' }),
      item('w', { createdAt: 500, workspaceId: 'ws2' }),
    ])

    const { result, unmount } = renderHook(() => useOutbox('a', 'ws1'))

    await waitFor(() => expect(result.current?.map((queued) => queued.id)).toEqual(['z', 'x', 'y']))
    unmount()
  })

  it('never shows another person’s items', async () => {
    await db.outbox.add(item('t1'))

    const { result, unmount } = renderHook(() => useOutbox('b', 'ws1'))

    await waitFor(() => expect(result.current).toEqual([]))
    unmount()
  })

  it('drops the previous person’s items at once when the user changes', async () => {
    await db.outbox.add(item('t1'))
    const { result, rerender, unmount } = renderHook(
      ({ userId }: { userId: string }) => useOutbox(userId, 'ws1'),
      { initialProps: { userId: 'a' } },
    )
    await waitFor(() => expect(result.current).toEqual([item('t1')]))

    rerender({ userId: 'b' })

    expect(result.current).toBeUndefined()
    await waitFor(() => expect(result.current).toEqual([]))
    unmount()
  })

  it('drops the previous family’s items at once when the family changes', async () => {
    await db.outbox.bulkAdd([item('t1'), item('t2', { createdAt: 2000, workspaceId: 'ws2' })])
    const { result, rerender, unmount } = renderHook(
      ({ workspaceId }: { workspaceId: string }) => useOutbox('a', workspaceId),
      { initialProps: { workspaceId: 'ws1' } },
    )
    await waitFor(() => expect(result.current).toEqual([item('t1')]))

    rerender({ workspaceId: 'ws2' })

    expect(result.current).toBeUndefined()
    await waitFor(() => expect(result.current?.map((queued) => queued.id)).toEqual(['t2']))
    unmount()
  })

  it('is empty without a user', () => {
    const { result, unmount } = renderHook(() => useOutbox(undefined, 'ws1'))

    expect(result.current).toEqual([])
    unmount()
  })
})

describe('usePendingTotals', () => {
  it('adds up the user’s pending items in the family', async () => {
    await db.outbox.bulkAdd([
      item('t1'),
      item('t2', {
        createdAt: 2000,
        payload: payload('t2', {
          amount: { amount: '5.00', currency: 'BRL' },
          occurredOn: '2026-10-31',
        }),
      }),
      item('t3', { userId: 'b' }),
    ])

    const { result, unmount } = renderHook(() => usePendingTotals('a', 'ws1', '2026-11'))

    expect(result.current).toEqual({ monthIncome: 0n, monthExpenses: 0n, balanceDelta: 0n })
    await waitFor(() =>
      expect(result.current).toEqual({
        monthIncome: 0n,
        monthExpenses: 1234n,
        balanceDelta: -1734n,
      }),
    )
    unmount()
  })
})
