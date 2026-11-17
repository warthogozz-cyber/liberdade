import type { components, operations } from './schema'

type Schemas = components['schemas']

export type MeView = Schemas['MeView']
export type MeUser = Schemas['MeUser']
export type MeWorkspace = Schemas['MeWorkspace']
export type ModuleView = Schemas['ModuleView']
export type ModuleKey = ModuleView['key']
export type WorkspaceView = Schemas['WorkspaceView']
export type AccountView = Schemas['AccountView']
export type AccountType = AccountView['type']
export type CreateAccountRequest = Schemas['CreateAccountRequest']
export type UpdateAccountRequest = Schemas['UpdateAccountRequest']
export type CategoryView = Schemas['CategoryView']
export type CategoryColor = NonNullable<CategoryView['color']>
export type CreateCategoryRequest = Schemas['CreateCategoryRequest']
export type UpdateCategoryRequest = Schemas['UpdateCategoryRequest']
export type TransactionView = Schemas['TransactionView']
export type CreateTransactionRequest = Schemas['CreateTransactionRequest']
export type UpdateTransactionRequest = Schemas['UpdateTransactionRequest']
export type DashboardView = Schemas['DashboardView']
export type DocumentStatus = Schemas['DocumentStatus']
export type AcceptDocumentsRequest = Schemas['AcceptDocumentsRequest']
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
export type MemberView = Schemas['MemberView']
export type MemberRole = MemberView['role']
export type InvitationView = Schemas['InvitationView']
export type InvitationPreview = Schemas['InvitationPreview']
export type AcceptedInvitation = Schemas['AcceptedInvitation']
export type CreateInvitationRequest = Schemas['CreateInvitationRequest']
export type UpdateMemberRequest = Schemas['UpdateMemberRequest']
/** What a person who is not signed in gives to join with a new passkey. */
export type AcceptNewUser = NonNullable<Schemas['NewUser']>
