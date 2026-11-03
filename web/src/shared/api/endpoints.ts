import { client, unwrap } from './client'
import type {
  AccountView,
  CategoryKind,
  CategoryView,
  CreateTransactionRequest,
  DashboardView,
  MeView,
  TransactionView,
  WorkspaceView,
} from './types'

export function getMe(): Promise<MeView> {
  return unwrap(client.GET('/v1/me'))
}

/** Creates the family in the browser's time zone (D28). */
export function createWorkspace(name: string): Promise<WorkspaceView> {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
  return unwrap(client.POST('/v1/workspaces', { body: { name, timezone } }))
}

export function listAccounts(ws: string): Promise<AccountView[]> {
  return unwrap(client.GET('/v1/workspaces/{ws}/accounts', { params: { path: { ws } } }))
}

export function listCategories(ws: string, kind: CategoryKind): Promise<CategoryView[]> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/categories', { params: { path: { ws }, query: { kind } } }),
  )
}

/** The month's transactions, newest first. `month` is `YYYY-MM`. */
export function listTransactions(ws: string, month: string): Promise<TransactionView[]> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/transactions', { params: { path: { ws }, query: { month } } }),
  )
}

export function getDashboard(ws: string, month: string): Promise<DashboardView> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/dashboard', { params: { path: { ws }, query: { month } } }),
  )
}

/** Records a transaction. Its id is the Idempotency-Key, so a resend returns the stored one. */
export function postTransaction(
  ws: string,
  payload: CreateTransactionRequest,
): Promise<TransactionView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/transactions', {
      params: { path: { ws }, header: { 'Idempotency-Key': payload.id } },
      body: payload,
    }),
  )
}
