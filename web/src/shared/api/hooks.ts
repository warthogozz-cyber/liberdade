import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
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

// One definition per query, shared by the hooks and `prefetchWorkspace`, so the prefetch fills
// exactly what the hooks read.
const accountsQuery = (ws: string) =>
  queryOptions({ queryKey: queryKeys.accounts(ws), queryFn: () => listAccounts(ws) })

const categoriesQuery = (ws: string, kind: CategoryKind) =>
  queryOptions({
    queryKey: queryKeys.categories(ws, kind),
    queryFn: () => listCategories(ws, kind),
  })

const transactionsQuery = (ws: string, month: string) =>
  queryOptions({
    queryKey: queryKeys.transactions(ws, month),
    queryFn: () => listTransactions(ws, month),
  })

const dashboardQuery = (ws: string, month: string) =>
  queryOptions({
    queryKey: queryKeys.dashboard(ws, month),
    queryFn: () => getDashboard(ws, month),
  })

export function useMe() {
  return useQuery({ queryKey: queryKeys.me, queryFn: getMe })
}

/** The signed-in user's family, or `undefined` while there is none. A user has one family for now. */
export function useCurrentWorkspace(): MeWorkspace | undefined {
  return useMe().data?.workspaces[0]
}

export function useAccounts(ws: string) {
  return useQuery(accountsQuery(ws))
}

export function useCategories(ws: string, kind: CategoryKind) {
  return useQuery(categoriesQuery(ws, kind))
}

export function useTransactions(ws: string, month: string) {
  return useQuery(transactionsQuery(ws, month))
}

export function useDashboard(ws: string, month: string) {
  return useQuery(dashboardQuery(ws, month))
}

/**
 * Loads what the family's screens read, so they still have it once the signal drops: the accounts,
 * both category lists, and the month's dashboard and transactions. A failed load is left out; the
 * screens try again themselves.
 */
export async function prefetchWorkspace(
  queryClient: QueryClient,
  workspaceId: string,
  month: string,
): Promise<void> {
  await Promise.all([
    queryClient.prefetchQuery(accountsQuery(workspaceId)),
    queryClient.prefetchQuery(categoriesQuery(workspaceId, 'EXPENSE')),
    queryClient.prefetchQuery(categoriesQuery(workspaceId, 'INCOME')),
    queryClient.prefetchQuery(dashboardQuery(workspaceId, month)),
    queryClient.prefetchQuery(transactionsQuery(workspaceId, month)),
  ])
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
