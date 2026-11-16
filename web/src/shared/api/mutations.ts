import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  archiveAccount,
  createAccount,
  deleteTransaction,
  restoreTransaction,
  unarchiveAccount,
  updateAccount,
  updateTransaction,
} from './endpoints'
import { queryKeys } from './queryKeys'
import type { CreateAccountRequest, UpdateAccountRequest, UpdateTransactionRequest } from './types'

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

/** Creates an account. */
export function useCreateAccount(ws: string) {
  return useMutation({
    mutationFn: (body: CreateAccountRequest) => createAccount(ws, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Changes an account: only the fields in `body`, so a balance adjustment sends nothing else. */
export function useUpdateAccount(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateAccountRequest }) =>
      updateAccount(ws, id, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Archives an account. */
export function useArchiveAccount(ws: string) {
  return useMutation({
    mutationFn: (id: string) => archiveAccount(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Brings an archived account back. */
export function useUnarchiveAccount(ws: string) {
  return useMutation({
    mutationFn: (id: string) => unarchiveAccount(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}
