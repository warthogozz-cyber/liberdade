import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  archiveAccount,
  archiveCategory,
  createAccount,
  createCategory,
  deleteTransaction,
  dismissGettingStarted,
  restoreTransaction,
  unarchiveAccount,
  unarchiveCategory,
  updateAccount,
  updateCategory,
  updateTransaction,
} from './endpoints'
import { queryKeys } from './queryKeys'
import type {
  CreateAccountRequest,
  CreateCategoryRequest,
  MeView,
  UpdateAccountRequest,
  UpdateCategoryRequest,
  UpdateTransactionRequest,
} from './types'

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

/** Creates a category or subcategory. */
export function useCreateCategory(ws: string) {
  return useMutation({
    mutationFn: (body: CreateCategoryRequest) => createCategory(ws, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Changes a category: only the fields in `body`. */
export function useUpdateCategory(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateCategoryRequest }) =>
      updateCategory(ws, id, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Archives a category with its subcategories. */
export function useArchiveCategory(ws: string) {
  return useMutation({
    mutationFn: (id: string) => archiveCategory(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Brings an archived category back. */
export function useUnarchiveCategory(ws: string) {
  return useMutation({
    mutationFn: (id: string) => unarchiveCategory(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}

/**
 * Closes the getting-started card. The cached `me` shows it dismissed at once and goes back if the
 * call fails; `onError` runs even after the card is gone, which is when a failure matters.
 */
export function useDismissGettingStarted(options: { onError?: () => void } = {}) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (workspaceId: string) => dismissGettingStarted(workspaceId),
    onMutate: async (workspaceId) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.me })
      const previous = queryClient.getQueryData<MeView>(queryKeys.me)
      const now = new Date().toISOString()
      queryClient.setQueryData<MeView>(
        queryKeys.me,
        (me) =>
          me && {
            ...me,
            workspaces: me.workspaces.map((workspace) =>
              workspace.id === workspaceId && workspace.gettingStartedDismissedAt === null
                ? { ...workspace, gettingStartedDismissedAt: now }
                : workspace,
            ),
          },
      )
      return { previous }
    },
    onError: (_error, _workspaceId, context) => {
      queryClient.setQueryData(queryKeys.me, context?.previous)
      options.onError?.()
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.me }),
  })
}
