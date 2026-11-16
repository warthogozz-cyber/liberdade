import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteTransaction, restoreTransaction, updateTransaction } from './endpoints'
import { queryKeys } from './queryKeys'
import type { UpdateTransactionRequest } from './types'

/** Every transaction mutation refreshes what the family owns once it ends, however it ends. */
function useRefreshWorkspace(ws: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.workspace(ws) })
}

/** Deletes one of the family's transactions. */
export function useDeleteTransaction(ws: string) {
  return useMutation({
    mutationFn: (id: string) => deleteTransaction(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Saves an edit of a transaction, with the `version` it was opened at. */
export function useUpdateTransaction(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTransactionRequest }) =>
      updateTransaction(ws, id, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Brings back a deleted transaction. */
export function useRestoreTransaction(ws: string) {
  return useMutation({
    mutationFn: (id: string) => restoreTransaction(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}
