import { useSyncExternalStore } from 'react'
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
  listModules,
  listTransactions,
} from './endpoints'
import { ApiError } from './errors'
import { queryKeys } from './queryKeys'
import type { CategoryKind, MeWorkspace, PaymentMethod, TransactionFilters } from './types'
import {
  readSelectedWorkspaceId,
  selectWorkspace,
  subscribeToWorkspaceChoice,
} from './workspaceChoice'

export { selectWorkspace }

// One definition per query, shared by the hooks and `prefetchWorkspace`, so the prefetch fills
// exactly what the hooks read.
const accountsQuery = (ws: string) =>
  queryOptions({ queryKey: queryKeys.accounts(ws), queryFn: () => listAccounts(ws) })

const categoriesQuery = (ws: string, kind: CategoryKind) =>
  queryOptions({
    queryKey: queryKeys.categories(ws, kind),
    queryFn: () => listCategories(ws, kind),
  })

const transactionsQuery = (ws: string, month: string, filters?: TransactionFilters) =>
  queryOptions({
    queryKey: queryKeys.transactions(ws, month, filters),
    queryFn: () => listTransactions(ws, month, filters),
  })

const dashboardQuery = (ws: string, month: string, paymentMethod?: PaymentMethod) =>
  queryOptions({
    queryKey: queryKeys.dashboard(ws, month, paymentMethod),
    queryFn: () => getDashboard(ws, month, paymentMethod),
  })

export function useMe() {
  return useQuery({ queryKey: queryKeys.me, queryFn: getMe })
}

const NO_WORKSPACES: MeWorkspace[] = []

/** Every family the signed-in user belongs to. */
export function useWorkspaces(): MeWorkspace[] {
  return useMe().data?.workspaces ?? NO_WORKSPACES
}

/**
 * The family this device chose (`selectWorkspace`), or the first one when the choice is not among
 * the user's families. `undefined` while there is none.
 */
export function useCurrentWorkspace(): MeWorkspace | undefined {
  return useChosenWorkspace(useWorkspaces())
}

/** The family this device chose among `workspaces`; see `useCurrentWorkspace`. */
export function useChosenWorkspace(workspaces: MeWorkspace[]): MeWorkspace | undefined {
  const chosen = useSyncExternalStore(
    subscribeToWorkspaceChoice,
    readSelectedWorkspaceId,
    () => null,
  )
  // Without a valid choice, a family that is not pending deletion comes first.
  return (
    workspaces.find((workspace) => workspace.id === chosen) ??
    workspaces.find((workspace) => workspace.deletionScheduledFor === null) ??
    workspaces[0]
  )
}

/** The family's modules; waits for a family id. */
export function useModules(ws: string | undefined) {
  return useQuery({
    queryKey: queryKeys.modules(ws ?? ''),
    queryFn: () => listModules(ws ?? ''),
    enabled: ws !== undefined,
  })
}

export function useAccounts(ws: string) {
  return useQuery(accountsQuery(ws))
}

export function useCategories(ws: string, kind: CategoryKind) {
  return useQuery(categoriesQuery(ws, kind))
}

export function useTransactions(ws: string, month: string, filters?: TransactionFilters) {
  return useQuery(transactionsQuery(ws, month, filters))
}

export function useDashboard(ws: string, month: string, paymentMethod?: PaymentMethod) {
  return useQuery(dashboardQuery(ws, month, paymentMethod))
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
