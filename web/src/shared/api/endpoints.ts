import { client, unwrap } from './client'
import type {
  AcceptDocumentsRequest,
  AccountView,
  DocumentStatus,
  CreateAccountRequest,
  UpdateAccountRequest,
  CategoryHint,
  CategoryHintItem,
  CategoryKind,
  CategoryView,
  CreateCategoryRequest,
  UpdateCategoryRequest,
  CreateTransactionRequest,
  DashboardView,
  ImportResult,
  ImportRow,
  MeView,
  ModuleView,
  PaymentMethod,
  TagView,
  TransactionFilters,
  TransactionView,
  UpdateTransactionRequest,
  WorkspaceView,
} from './types'

export function getMe(): Promise<MeView> {
  return unwrap(client.GET('/v1/me'))
}

/** The terms and the privacy policy, each with the version in force and the one the person accepted. */
export function listDocuments(): Promise<DocumentStatus[]> {
  return unwrap(client.GET('/v1/me/documents'))
}

/** Accepts both documents at these versions. A stale version is a 409 `documents.version-outdated`. */
export async function acceptDocuments(body: AcceptDocumentsRequest): Promise<void> {
  await unwrap(client.POST('/v1/me/documents/accept', { body }))
}

/** Closes the getting-started card for the signed-in member. A repeat keeps the first date. */
export async function dismissGettingStarted(workspaceId: string): Promise<void> {
  await unwrap(
    client.POST('/v1/me/getting-started/dismiss', { params: { query: { workspaceId } } }),
  )
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
  { categoryId, accountId, tagId, paymentMethod }: TransactionFilters = {},
): Promise<TransactionView[]> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/transactions', {
      params: { path: { ws }, query: { month, categoryId, accountId, tagId, paymentMethod } },
    }),
  )
}

export function getDashboard(
  ws: string,
  month: string,
  paymentMethod?: PaymentMethod,
): Promise<DashboardView> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/dashboard', {
      params: { path: { ws }, query: { month, paymentMethod } },
    }),
  )
}

/** The family's active tags (a Youth gets the ones they may use). */
export function listTags(ws: string): Promise<TagView[]> {
  return unwrap(client.GET('/v1/workspaces/{ws}/tags', { params: { path: { ws } } }))
}

/** Renames a tag. A name an active tag already has is a 409 `tag.name-taken`. */
export function renameTag(ws: string, id: string, name: string): Promise<TagView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/tags/{id}', { params: { path: { ws, id } }, body: { name } }),
  )
}

/** Archives a tag; archiving it again changes nothing. */
export async function archiveTag(ws: string, id: string): Promise<void> {
  await unwrap(
    client.POST('/v1/workspaces/{ws}/tags/{id}/archive', { params: { path: { ws, id } } }),
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

/** The family's four modules, switched on or off. Every role may read them. */
export function listModules(ws: string): Promise<ModuleView[]> {
  return unwrap(client.GET('/v1/workspaces/{ws}/modules', { params: { path: { ws } } }))
}

/** Sends up to 500 confirmed statement lines. A resend counts the lines already saved as `skipped`. */
export function importStatement(
  ws: string,
  accountId: string,
  rows: ImportRow[],
): Promise<ImportResult> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/accounts/{accountId}/imports', {
      params: { path: { ws, accountId } },
      body: { rows },
    }),
  )
}

/** The category each past `(kind, description)` used, in the order asked; `null` when none fits. */
export async function categoryHints(
  ws: string,
  items: CategoryHintItem[],
): Promise<CategoryHint[]> {
  const { hints } = await unwrap(
    client.POST('/v1/workspaces/{ws}/imports/category-hints', {
      params: { path: { ws } },
      body: { items },
    }),
  )
  return hints
}
