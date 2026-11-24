import { waitFor } from '@testing-library/react'
import { act } from 'react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderHookWithProviders } from '@/shared/testing'
import { useImport, type ImportTotals } from './useImport'
import type { PreviewLine } from './preview'

const ACCOUNT = '0192f5a0-0000-7000-8000-000000000001'
const CATEGORY = '0192f5a0-0000-7000-8000-0000000000c1'

function row(i: number, over: Partial<PreviewLine> = {}): PreviewLine {
  return {
    occurredOn: '2026-11-03',
    cents: -4590n,
    description: `Loja ${i}`,
    kind: 'EXPENSE',
    absCents: 4590n,
    billPayment: false,
    id: `id-${i}`,
    importKey: `key-${i}`,
    state: 'normal',
    checked: true,
    categoryId: CATEGORY,
    confirmsPlannedId: null,
    ...over,
  }
}

interface Sent {
  rows: Array<Record<string, unknown>>
}

/** Answers every import call with one confirmed row and the rest created, or fails the nth call. */
function mockImport(failOn?: number) {
  const calls: Sent[] = []
  server.use(
    http.post(`${API_URL}/v1/workspaces/:ws/accounts/:accountId/imports`, async ({ request }) => {
      const body = (await request.json()) as Sent
      calls.push(body)
      if (calls.length === failOn) {
        return HttpResponse.json({ status: 500, code: 'internal' }, { status: 500 })
      }
      return HttpResponse.json({ created: body.rows.length - 1, confirmed: 1, skipped: 0 })
    }),
  )
  return calls
}

const many = (n: number) => Array.from({ length: n }, (_, i) => row(i))

describe('useImport', () => {
  it('sendsInBatchesOf500', async () => {
    const ws = seedMockFamily().id
    const calls = mockImport()
    const { result } = renderHookWithProviders(() => useImport(ws, ACCOUNT))

    let totals: ImportTotals | undefined
    await act(async () => {
      totals = await result.current.send(many(1200))
    })

    expect(calls.map((c) => c.rows.length)).toEqual([500, 500, 200])
    expect(totals).toEqual({ created: 1197, confirmed: 3, skipped: 0, failedAfter: null })
    expect(calls[0].rows[0].id).toBe('id-0')
    expect(calls[2].rows[199].id).toBe('id-1199')
  })

  it('stopsAtTheFailedBatch', async () => {
    const ws = seedMockFamily().id
    const calls = mockImport(2)
    const { result } = renderHookWithProviders(() => useImport(ws, ACCOUNT))

    let totals: ImportTotals | undefined
    await act(async () => {
      totals = await result.current.send(many(1200))
    })

    expect(calls).toHaveLength(2)
    expect(totals).toEqual({ created: 499, confirmed: 1, skipped: 0, failedAfter: 500 })
  })

  it('sendsOnlyCheckedRows', async () => {
    const ws = seedMockFamily().id
    const calls = mockImport()
    const { result } = renderHookWithProviders(() => useImport(ws, ACCOUNT))

    await act(async () => {
      await result.current.send([
        row(0, { description: 'x'.repeat(200), absCents: 123456n }),
        row(1, { checked: false }),
        row(2, { kind: 'INCOME', confirmsPlannedId: 'planned-1' }),
      ])
    })

    expect(calls).toHaveLength(1)
    expect(calls[0].rows).toHaveLength(2)
    expect(calls[0].rows[0]).toMatchObject({
      id: 'id-0',
      kind: 'EXPENSE',
      amount: { amount: '1234.56', currency: 'BRL' },
      occurredOn: '2026-11-03',
      description: 'x'.repeat(140),
      categoryId: CATEGORY,
      importKey: 'key-0',
    })
    expect(calls[0].rows[1]).toMatchObject({ kind: 'INCOME', confirmsPlannedId: 'planned-1' })
  })

  it('sendsNothingWhenNothingIsChecked', async () => {
    const ws = seedMockFamily().id
    const calls = mockImport()
    const { result } = renderHookWithProviders(() => useImport(ws, ACCOUNT))

    let totals: ImportTotals | undefined
    await act(async () => {
      totals = await result.current.send([row(0, { checked: false })])
    })

    expect(calls).toHaveLength(0)
    expect(totals).toEqual({ created: 0, confirmed: 0, skipped: 0, failedAfter: null })
  })

  it('refusesACheckedLineWithoutACategory', async () => {
    const ws = seedMockFamily().id
    const calls = mockImport()
    const { result } = renderHookWithProviders(() => useImport(ws, ACCOUNT))

    await expect(result.current.send([row(0), row(1, { categoryId: null })])).rejects.toThrow(
      'A checked line has no category',
    )
    expect(calls).toHaveLength(0)
  })

  it('invalidatesTheFamilyQueriesAtTheEnd', async () => {
    const ws = seedMockFamily().id
    mockImport()
    const { result, queryClient } = renderHookWithProviders(() => useImport(ws, ACCOUNT))
    queryClient.setQueryData(['ws', ws, 'transactions', '2026-11'], [])
    queryClient.setQueryData(['ws', ws, 'planning', 'budgets'], [])

    await act(async () => {
      await result.current.send([row(0)])
    })

    await waitFor(() => {
      expect(queryClient.getQueryState(['ws', ws, 'transactions', '2026-11'])?.isInvalidated).toBe(
        true,
      )
      expect(queryClient.getQueryState(['ws', ws, 'planning', 'budgets'])?.isInvalidated).toBe(true)
    })
  })
})
