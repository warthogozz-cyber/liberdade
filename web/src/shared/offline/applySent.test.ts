import { describe, expect, it } from 'vitest'
import { queryKeys, type DashboardView, type TransactionView } from '@/shared/api'
import { applySentTransaction } from '@/shared/offline'
import {
  createTestQueryClient,
  outboxPayload as payload,
  transactionView as viewOf,
} from '@/shared/testing'

const ws = 'ws1'

type Totals = Partial<Record<'income' | 'expenses' | 'result' | 'balance', string>>

function dashboard(month: string, totals: Totals): DashboardView {
  const brl = (amount = '0.00') => ({ amount, currency: 'BRL' })
  return {
    month,
    income: brl(totals.income),
    expenses: brl(totals.expenses),
    result: brl(totals.result),
    balance: brl(totals.balance),
    byCategory: [],
    dailyBalance: [],
  }
}

function row(id: string, occurredOn: string, createdAt = '2026-11-03T12:00:00Z') {
  return viewOf(payload(id, { occurredOn }), createdAt)
}

describe('applySentTransaction', () => {
  it('adds an expense to its month and result, takes it from the balance and lists it', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', { expenses: '10.00', result: '-10.00', balance: '90.00' }),
    )
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), [
      row('newer', '2026-11-05'),
      row('same-day-later', '2026-11-03', '2026-11-03T13:00:00Z'),
      row('same-day-earlier', '2026-11-03', '2026-11-03T11:00:00Z'),
      row('older', '2026-11-01'),
    ])

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03'))

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', { expenses: '22.34', result: '-22.34', balance: '77.66' }),
    )
    expect(listedIds(queryClient, '2026-11')).toEqual([
      'newer',
      'same-day-later',
      'sent',
      'same-day-earlier',
      'older',
    ])
    expect(queryClient.getQueryState(queryKeys.dashboard(ws, '2026-11'))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(queryKeys.transactions(ws, '2026-11'))?.isInvalidated).toBe(
      true,
    )
  })

  it('orders rows made in the same second by the instant, whatever its precision', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), [
      row('later', '2026-11-03', '2026-11-03T12:00:00.100Z'),
    ])

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03', '2026-11-03T12:00:00Z'))

    expect(listedIds(queryClient, '2026-11')).toEqual(['later', 'sent'])
  })

  it('orders rows made at the same instant by the highest id', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), [
      row('c', '2026-11-03'),
      row('a', '2026-11-03'),
    ])

    applySentTransaction(queryClient, ws, row('b', '2026-11-03'))

    expect(listedIds(queryClient, '2026-11')).toEqual(['c', 'b', 'a'])
  })

  it('takes an expense of another month only from the balance, and caches nothing new', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', { expenses: '10.00', balance: '5.00' }),
    )

    applySentTransaction(queryClient, ws, row('sent', '2026-10-31'))

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', { expenses: '10.00', balance: '-7.34' }),
    )
    expect(queryClient.getQueryCache().getAll()).toHaveLength(1)
  })

  it('adds an income to its month, its result and every balance, listed at the end', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-10'),
      dashboard('2026-10', { expenses: '3.00', result: '-3.00', balance: '90.00' }),
    )
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', { expenses: '10.00', balance: '90.00' }),
    )
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-10'), [row('newer', '2026-10-31')])

    applySentTransaction(queryClient, ws, {
      ...row('sent', '2026-10-01'),
      kind: 'INCOME',
    })

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-10'))).toEqual(
      dashboard('2026-10', {
        income: '12.34',
        expenses: '3.00',
        result: '9.34',
        balance: '102.34',
      }),
    )
    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', { expenses: '10.00', balance: '102.34' }),
    )
    expect(listedIds(queryClient, '2026-10')).toEqual(['newer', 'sent'])
  })

  it('leaves a list that already has the transaction alone', () => {
    const queryClient = createTestQueryClient()
    const list = [row('sent', '2026-11-03')]
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), list)

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03'))

    expect(queryClient.getQueryData(queryKeys.transactions(ws, '2026-11'))).toEqual(list)
  })

  it('transferJoinsListOnly', () => {
    const queryClient = createTestQueryClient()
    const before = dashboard('2026-11', {
      income: '5.00',
      expenses: '10.00',
      result: '-5.00',
      balance: '90.00',
    })
    queryClient.setQueryData(queryKeys.dashboard(ws, '2026-11'), before)
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), [row('newer', '2026-11-05')])

    applySentTransaction(queryClient, ws, {
      ...row('sent', '2026-11-03'),
      kind: 'TRANSFER',
      categoryId: null,
      toAccountId: 'other-account',
    })

    // Both accounts are active, so the family's totals stay as they were (Ruling R16).
    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(before)
    expect(listedIds(queryClient, '2026-11')).toEqual(['newer', 'sent'])
    expect(queryClient.getQueryState(queryKeys.dashboard(ws, '2026-11'))?.isInvalidated).toBe(true)
  })

  it('leaves other families alone', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard('ws2', '2026-11'),
      dashboard('2026-11', { expenses: '10.00', balance: '90.00' }),
    )

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03'))

    expect(queryClient.getQueryData(queryKeys.dashboard('ws2', '2026-11'))).toEqual(
      dashboard('2026-11', { expenses: '10.00', balance: '90.00' }),
    )
    expect(queryClient.getQueryState(queryKeys.dashboard('ws2', '2026-11'))?.isInvalidated).toBe(
      false,
    )
  })
})

function listedIds(queryClient: ReturnType<typeof createTestQueryClient>, month: string) {
  return queryClient
    .getQueryData<TransactionView[]>(queryKeys.transactions(ws, month))
    ?.map((listed) => listed.id)
}
