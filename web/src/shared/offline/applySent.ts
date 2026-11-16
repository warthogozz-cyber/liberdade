import type { QueryClient } from '@tanstack/react-query'
import { queryKeys, type ApiMoney, type DashboardView, type TransactionView } from '@/shared/api'
import { centsToApi, parseCents } from '@/shared/money'

/**
 * The API's order for a month's list: newest date, then newest record, then highest id. The
 * instants are compared as times, since the API may write them with any fractional precision.
 */
function newestFirst(a: TransactionView, b: TransactionView): number {
  return (
    b.occurredOn.localeCompare(a.occurredOn) ||
    Date.parse(b.createdAt) - Date.parse(a.createdAt) ||
    b.id.localeCompare(a.id)
  )
}

/** The list with `view` where the API would put it; unchanged if a refetch already brought it. */
function withRow(list: TransactionView[], view: TransactionView): TransactionView[] {
  if (list.some((row) => row.id === view.id)) return list
  const index = list.findIndex((row) => newestFirst(view, row) < 0)
  return index === -1 ? [...list, view] : [...list.slice(0, index), view, ...list.slice(index)]
}

function shift(money: ApiMoney, cents: bigint): ApiMoney {
  return { ...money, amount: centsToApi(parseCents(money.amount) + cents) }
}

/**
 * Puts a sent transaction into the cache before the refetch, so the home screen does not flicker:
 * an expense adds to its month's expenses and leaves every cached balance, an income adds to every
 * cached balance, and either joins its month's list when that list is cached. Nothing new is cached.
 * Then the family's queries are refetched.
 */
export function applySentTransaction(
  queryClient: QueryClient,
  workspaceId: string,
  view: TransactionView,
): void {
  if (view.kind === 'EXPENSE' || view.kind === 'INCOME') {
    const month = view.occurredOn.slice(0, 7)
    const cents = parseCents(view.amount.amount)
    queryClient.setQueriesData<DashboardView>(
      { queryKey: [...queryKeys.workspace(workspaceId), 'dashboard'] },
      (dashboard) =>
        dashboard && {
          ...dashboard,
          balance: shift(dashboard.balance, view.kind === 'EXPENSE' ? -cents : cents),
        },
    )
    if (view.kind === 'EXPENSE') {
      queryClient.setQueryData<DashboardView>(
        queryKeys.dashboard(workspaceId, month),
        (dashboard) => dashboard && { ...dashboard, expenses: shift(dashboard.expenses, cents) },
      )
    }
    queryClient.setQueryData<TransactionView[]>(
      queryKeys.transactions(workspaceId, month),
      (list) => list && withRow(list, view),
    )
  }
  void queryClient.invalidateQueries({ queryKey: queryKeys.workspace(workspaceId) })
}
