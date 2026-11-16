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
 * Puts a sent transaction into the cache before the refetch, so the home screen does not flicker
 * (Ruling R16). An income adds to its month's income and result and to every cached balance; an
 * expense adds to its month's expenses, and takes from its month's result and every cached balance;
 * a transfer moves money between two active accounts, so it changes no total. Each joins its month's
 * list when that list is cached. Nothing new is cached, and the category ranking and the daily
 * balance wait for the refetch. Then the family's queries are refetched.
 */
export function applySentTransaction(
  queryClient: QueryClient,
  workspaceId: string,
  view: TransactionView,
): void {
  const month = view.occurredOn.slice(0, 7)
  if (view.kind !== 'TRANSFER') {
    const cents = parseCents(view.amount.amount)
    const signed = view.kind === 'INCOME' ? cents : -cents
    queryClient.setQueriesData<DashboardView>(
      { queryKey: [...queryKeys.workspace(workspaceId), 'dashboard'] },
      (dashboard) => dashboard && { ...dashboard, balance: shift(dashboard.balance, signed) },
    )
    queryClient.setQueryData<DashboardView>(
      queryKeys.dashboard(workspaceId, month),
      (dashboard) =>
        dashboard && {
          ...dashboard,
          ...(view.kind === 'INCOME'
            ? { income: shift(dashboard.income, cents) }
            : { expenses: shift(dashboard.expenses, cents) }),
          result: shift(dashboard.result, signed),
        },
    )
  }
  queryClient.setQueryData<TransactionView[]>(
    queryKeys.transactions(workspaceId, month),
    (list) => list && withRow(list, view),
  )
  void queryClient.invalidateQueries({ queryKey: queryKeys.workspace(workspaceId) })
}
