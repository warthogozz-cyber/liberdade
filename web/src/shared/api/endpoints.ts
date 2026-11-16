import { client, unwrap } from './client'
import type {
  AccountView,
  CreateAccountRequest,
  UpdateAccountRequest,
  CategoryKind,
  CategoryView,
  CreateCategoryRequest,
  UpdateCategoryRequest,
  CreateTransactionRequest,
  DashboardView,
  MeView,
  TransactionFilters,
  TransactionView,
  UpdateTransactionRequest,
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

/** The month's transactions, newest first. `month` is `YYYY-MM`; a category takes its subcategories along. */
export function listTransactions(
  ws: string,
  month: string,
  { categoryId, accountId }: TransactionFilters = {},
): Promise<TransactionView[]> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/transactions', {
      params: { path: { ws }, query: { month, categoryId, accountId } },
    }),
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
  signal?: AbortSignal,
): Promise<TransactionView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/transactions', {
      params: { path: { ws }, header: { 'Idempotency-Key': payload.id } },
      body: payload,
      signal,
    }),
  )
}

/** Deletes a transaction. A second delete of the same one is a no-op; an unknown id is a 404. */
export async function deleteTransaction(ws: string, id: string): Promise<void> {
  await unwrap(
    client.DELETE('/v1/workspaces/{ws}/transactions/{id}', { params: { path: { ws, id } } }),
  )
}

/** Saves an edit of a transaction. A stale `version` is a 409 `transaction.version-conflict`. */
export function updateTransaction(
  ws: string,
  id: string,
  body: UpdateTransactionRequest,
): Promise<TransactionView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/transactions/{id}', { params: { path: { ws, id } }, body }),
  )
}

/** Brings a deleted transaction back. An active one comes back unchanged. */
export function restoreTransaction(ws: string, id: string): Promise<TransactionView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/transactions/{id}/restore', { params: { path: { ws, id } } }),
  )
}

/** Creates an account. A name an active account already has is a 409 `account.name-taken`. */
export function createAccount(ws: string, body: CreateAccountRequest): Promise<AccountView> {
  return unwrap(client.POST('/v1/workspaces/{ws}/accounts', { params: { path: { ws } }, body }))
}

/** Changes the fields sent: the name, the type, or the `balance` to reach today. */
export function updateAccount(
  ws: string,
  id: string,
  body: UpdateAccountRequest,
): Promise<AccountView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/accounts/{id}', { params: { path: { ws, id } }, body }),
  )
}

/** Archives an account. The last active one is a 422 `account.last-active`. */
export function archiveAccount(ws: string, id: string): Promise<AccountView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/accounts/{id}/archive', { params: { path: { ws, id } } }),
  )
}

/** Brings an archived account back. */
export function unarchiveAccount(ws: string, id: string): Promise<AccountView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/accounts/{id}/unarchive', { params: { path: { ws, id } } }),
  )
}

/** Creates a category or subcategory. A name taken under the same parent is a 409 `category.name-taken`. */
export function createCategory(ws: string, body: CreateCategoryRequest): Promise<CategoryView> {
  return unwrap(client.POST('/v1/workspaces/{ws}/categories', { params: { path: { ws } }, body }))
}

/** Changes the fields sent: the name, the icon or the color. The kind and the parent never change. */
export function updateCategory(
  ws: string,
  id: string,
  body: UpdateCategoryRequest,
): Promise<CategoryView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/categories/{id}', { params: { path: { ws, id } }, body }),
  )
}

/** Archives a category with its subcategories. The last active one of a kind is a 422 `category.last-active`. */
export function archiveCategory(ws: string, id: string): Promise<CategoryView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/categories/{id}/archive', { params: { path: { ws, id } } }),
  )
}

/** Brings an archived category back, with the subcategories archived along with it. */
export function unarchiveCategory(ws: string, id: string): Promise<CategoryView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/categories/{id}/unarchive', { params: { path: { ws, id } } }),
  )
}
