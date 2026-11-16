import type { components, operations } from './schema'

type Schemas = components['schemas']

export type MeView = Schemas['MeView']
export type MeUser = Schemas['MeUser']
export type MeWorkspace = Schemas['MeWorkspace']
export type WorkspaceView = Schemas['WorkspaceView']
export type AccountView = Schemas['AccountView']
export type CategoryView = Schemas['CategoryView']
export type TransactionView = Schemas['TransactionView']
export type CreateTransactionRequest = Schemas['CreateTransactionRequest']
export type UpdateTransactionRequest = Schemas['UpdateTransactionRequest']
export type DashboardView = Schemas['DashboardView']
export type TransactionKind = CreateTransactionRequest['kind']
/** The API's money: a decimal text and a currency. Named apart from `Money` in `@/shared/money`. */
export type ApiMoney = Schemas['Money']
/** The kinds a category list can be asked for. Transfers have no categories (D23). */
export type CategoryKind = operations['listCategories']['parameters']['query']['kind']
/** What narrows the month's list: a category (with its subcategories) and an account. */
export interface TransactionFilters {
  categoryId?: string
  accountId?: string
}
