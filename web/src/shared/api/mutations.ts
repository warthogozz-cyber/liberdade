import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  archiveAccount,
  archiveCategory,
  createAccount,
  createCategory,
  createInvitation,
  createRecoveryLink,
  acceptInvitation,
  deleteTransaction,
  previewInvitation,
  removeMember,
  renameWorkspace,
  revokeInvitation,
  updateMember,
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
  AcceptNewUser,
  CreateAccountRequest,
  CreateInvitationRequest,
  UpdateMemberRequest,
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

/** The family and the signed-in person's own row both read these, so a change refreshes both. */
function useRefreshFamily(ws: string) {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.workspace(ws) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.me }),
    ])
}

/** Changes a member's role or nickname. */
export function useUpdateMember(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateMemberRequest }) =>
      updateMember(ws, id, body),
    onSettled: useRefreshFamily(ws),
  })
}

/** Takes a member out of the family. */
export function useRemoveMember(ws: string) {
  return useMutation({
    mutationFn: (id: string) => removeMember(ws, id),
    onSettled: useRefreshFamily(ws),
  })
}

/** Creates an invitation; the answer carries the link's token once. */
export function useCreateInvitation(ws: string) {
  return useMutation({
    mutationFn: (body: CreateInvitationRequest) => createInvitation(ws, body),
    onSettled: useRefreshFamily(ws),
  })
}

export function useRevokeInvitation(ws: string) {
  return useMutation({
    mutationFn: (id: string) => revokeInvitation(ws, id),
    onSettled: useRefreshFamily(ws),
  })
}

/** Makes a recovery link for a member; the answer carries the link's token once. */
export function useCreateRecoveryLink(ws: string) {
  return useMutation({ mutationFn: (memberId: string) => createRecoveryLink(ws, memberId) })
}

export function useRenameWorkspace(ws: string) {
  return useMutation({
    mutationFn: (name: string) => renameWorkspace(ws, name),
    onSettled: useRefreshFamily(ws),
  })
}

/**
 * Reads a link. A mutation, not a query: the token travels in the body, and a query would put it in
 * a cache key.
 */
export function usePreviewInvitation() {
  return useMutation({ mutationFn: (token: string) => previewInvitation(token) })
}

/** Joins the family (or recovers access) with a link's token. */
export function useAcceptInvitation() {
  return useMutation({
    mutationFn: ({ token, newUser }: { token: string; newUser?: AcceptNewUser }) =>
      acceptInvitation(token, newUser),
  })
}
