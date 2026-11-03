import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createWorkspace,
  getDashboard,
  getMe,
  listAccounts,
  listCategories,
  listTransactions,
} from './endpoints'
import { ApiError } from './errors'
import { queryKeys } from './queryKeys'
import type { CategoryKind, MeWorkspace } from './types'

export function useMe() {
  return useQuery({ queryKey: queryKeys.me, queryFn: getMe })
}

/** The signed-in user's family, or `undefined` while there is none. A user has one family for now. */
export function useCurrentWorkspace(): MeWorkspace | undefined {
  return useMe().data?.workspaces[0]
}

export function useAccounts(ws: string) {
  return useQuery({ queryKey: queryKeys.accounts(ws), queryFn: () => listAccounts(ws) })
}

export function useCategories(ws: string, kind: CategoryKind) {
  return useQuery({
    queryKey: queryKeys.categories(ws, kind),
    queryFn: () => listCategories(ws, kind),
  })
}

export function useTransactions(ws: string, month: string) {
  return useQuery({
    queryKey: queryKeys.transactions(ws, month),
    queryFn: () => listTransactions(ws, month),
  })
}

export function useDashboard(ws: string, month: string) {
  return useQuery({
    queryKey: queryKeys.dashboard(ws, month),
    queryFn: () => getDashboard(ws, month),
  })
}

/**
 * Creates the family, then waits for `useMe` to show it. A 409 `workspace.already-exists` counts as
 * success: it is the other tap of a double tap, or another tab.
 */
export function useCreateWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (name: string) => {
      try {
        await createWorkspace(name)
      } catch (error) {
        if (!(error instanceof ApiError && error.code === 'workspace.already-exists')) throw error
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.me }),
  })
}
