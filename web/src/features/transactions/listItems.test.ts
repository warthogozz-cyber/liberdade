import { describe, expect, it } from 'vitest'
import { groupByDay, mergeTransactions } from '@/features/transactions/listItems'
import { outboxItem, outboxPayload, transactionView } from '@/shared/testing'

describe('mergeTransactions', () => {
  it('keeps the API copy of an id that is also in the queue', () => {
    const merged = mergeTransactions([transactionView(outboxPayload('t1'))], [outboxItem('t1')])

    expect(merged).toHaveLength(1)
    expect(merged[0].outbox).toBeUndefined()
  })

  it('sorts by date, then by creation, newest first', () => {
    const merged = mergeTransactions(
      [
        transactionView(outboxPayload('old', { occurredOn: '2026-11-14' }), '2026-11-14T10:00:00Z'),
        transactionView(
          outboxPayload('day15', { occurredOn: '2026-11-15' }),
          '2026-11-15T09:00:00Z',
        ),
      ],
      [
        outboxItem('new', {
          payload: outboxPayload('new', { occurredOn: '2026-11-14' }),
          createdAt: Date.parse('2026-11-14T12:00:00Z'),
        }),
      ],
    )

    expect(merged.map((item) => item.id)).toEqual(['day15', 'new', 'old'])
    expect(merged[1]).toMatchObject({
      createdAtMs: Date.parse('2026-11-14T12:00:00Z'),
      description: null,
      outbox: outboxItem('new', {
        payload: outboxPayload('new', { occurredOn: '2026-11-14' }),
        createdAt: Date.parse('2026-11-14T12:00:00Z'),
      }),
    })
  })

  it('lists only the queue while the API has not answered', () => {
    const merged = mergeTransactions(undefined, [outboxItem('t1')])

    expect(merged.map((item) => item.id)).toEqual(['t1'])
  })
})

describe('groupByDay', () => {
  it('groups by date, newest day first, in the order given', () => {
    const items = mergeTransactions(
      [
        transactionView(outboxPayload('a', { occurredOn: '2026-11-01' })),
        transactionView(outboxPayload('b', { occurredOn: '2026-11-15' })),
        transactionView(outboxPayload('c', { occurredOn: '2026-11-14' })),
        transactionView(outboxPayload('d', { occurredOn: '2026-11-15' })),
      ],
      [],
    )

    const groups = groupByDay(items)

    expect(groups.map((group) => group.date)).toEqual(['2026-11-15', '2026-11-14', '2026-11-01'])
    expect(groups[0].items.map((item) => item.id)).toEqual(['d', 'b'])
  })
})
