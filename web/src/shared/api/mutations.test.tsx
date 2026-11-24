import { act, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import {
  ApiError,
  deleteTransaction,
  queryKeys,
  useDeleteTransaction,
  useUpdateAccount,
} from '@/shared/api'
import { planningKeys } from '@/shared/api/planning'
import { outboxPayload, renderHookWithProviders, transactionView } from '@/shared/testing'

const ID = '0192f3a4-0000-7000-8000-0000000000d1'

/** Stores a transaction in the mock API and returns the family's id. */
function seedTransaction(): string {
  const workspace = seedMockFamily()
  mockState.transactions.push(
    transactionView(outboxPayload(ID, { accountId: mockState.accounts[0].id })),
  )
  return workspace.id
}

describe('deleteTransaction', () => {
  it('deletes the transaction and resolves with nothing', async () => {
    const ws = seedTransaction()

    await expect(deleteTransaction(ws, ID)).resolves.toBeUndefined()

    expect(mockState.deletedTransactionIds.has(ID)).toBe(true)
  })

  it('rejects with the API error for an unknown transaction', async () => {
    const ws = seedTransaction()

    const error: unknown = await deleteTransaction(
      ws,
      '0192f3a4-0000-7000-8000-0000000000d2',
    ).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404 })
  })
})

describe('useDeleteTransaction', () => {
  it('deletes, then refreshes everything the family owns', async () => {
    const ws = seedTransaction()
    const { result, queryClient } = renderHookWithProviders(() => useDeleteTransaction(ws))
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    await act(() => result.current.mutateAsync(ID))

    expect(mockState.deletedTransactionIds.has(ID)).toBe(true)
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.workspace(ws) }),
    )
  })
})

describe('useUpdateAccount', () => {
  it('also invalidates what planning shows, which sits under the family key', async () => {
    const workspace = seedMockFamily()
    const ws = workspace.id
    const { result, queryClient } = renderHookWithProviders(() => useUpdateAccount(ws))
    queryClient.setQueryData(planningKeys.reserve(ws), { seeded: true })
    expect(queryClient.getQueryState(planningKeys.reserve(ws))?.isInvalidated).toBe(false)

    await act(() =>
      result.current.mutateAsync({ id: mockState.accounts[0].id, body: { isReserve: true } }),
    )

    await waitFor(() =>
      expect(queryClient.getQueryState(planningKeys.reserve(ws))?.isInvalidated).toBe(true),
    )
  })
})
