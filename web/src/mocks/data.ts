import type { MeUser, MeWorkspace, TransactionView } from '@/shared/api'
import type { components } from '@/shared/api/schema'

type Schemas = components['schemas']

/** An account as stored; its balance is worked out from the transactions. */
export type MockAccount = Omit<Schemas['AccountView'], 'balance'>

export interface MockState {
  /** `false` makes every `/v1` route but `/v1/csrf` answer 401 `auth.required`. */
  signedIn: boolean
  user: MeUser
  suggestedWorkspaceName: string
  /** The user's families: none at first, at most one. */
  workspaces: MeWorkspace[]
  accounts: MockAccount[]
  /** In seed order: the 13 expense categories, then the 6 income ones. */
  categories: Schemas['CategoryView'][]
  /** Every stored transaction, deleted or not: a deleted id stays taken and restorable. */
  transactions: TransactionView[]
  /** Ids of the deleted transactions, left out of every list and sum. */
  deletedTransactionIds: Set<string>
}

// The seed of LedgerSeeder.java: same emojis, names and order.
const EXPENSE_CATEGORIES: [icon: string, name: string][] = [
  ['🛒', 'Mercado'],
  ['🍽️', 'Alimentação fora'],
  ['🚌', 'Transporte'],
  ['🏠', 'Moradia'],
  ['💊', 'Saúde'],
  ['🎉', 'Lazer'],
  ['💡', 'Contas e serviços'],
  ['🛍️', 'Compras'],
  ['📚', 'Educação'],
  ['🐾', 'Pets'],
  ['🎁', 'Presentes e doações'],
  ['🧾', 'Impostos e taxas'],
  ['📦', 'Outros'],
]

const INCOME_CATEGORIES: [icon: string, name: string][] = [
  ['💼', 'Salário'],
  ['✨', 'Renda extra'],
  ['📈', 'Rendimentos'],
  ['↩️', 'Reembolsos'],
  ['🎁', 'Presentes'],
  ['📦', 'Outros'],
]

function initialState(): MockState {
  return {
    signedIn: true,
    user: {
      id: '0192f3a4-0000-7000-8000-000000000001',
      displayName: 'Ana Teste',
      passkeyCreatedAt: null,
    },
    suggestedWorkspaceName: 'Família Teste',
    workspaces: [],
    accounts: [],
    categories: [],
    transactions: [],
    deletedTransactionIds: new Set(),
  }
}

/** The mock API's data: the session is open, for Ana Teste, still without a family. */
export const mockState: MockState = initialState()

export function resetMockData(): void {
  Object.assign(mockState, initialState())
}

/** Creates a family as `POST /v1/workspaces` does: with the wallet and the default categories. */
export function createFamily(name: string, timezone: string): MeWorkspace {
  const workspace: MeWorkspace = {
    id: crypto.randomUUID(),
    membershipId: crypto.randomUUID(),
    name,
    role: 'OWNER',
    currency: 'BRL',
    timezone,
  }
  mockState.workspaces.push(workspace)
  mockState.accounts.push({
    id: crypto.randomUUID(),
    name: 'Carteira',
    type: 'CASH',
    currency: workspace.currency,
    initialBalance: { amount: '0.00', currency: workspace.currency },
    ownerMembershipId: workspace.membershipId,
    archivedAt: null,
  })
  mockState.categories.push(
    ...EXPENSE_CATEGORIES.map(([icon, categoryName]) => ({
      id: crypto.randomUUID(),
      kind: 'EXPENSE' as const,
      icon,
      color: null,
      parentId: null,
      archivedAt: null,
      name: categoryName,
    })),
    ...INCOME_CATEGORIES.map(([icon, categoryName]) => ({
      id: crypto.randomUUID(),
      kind: 'INCOME' as const,
      icon,
      color: null,
      parentId: null,
      archivedAt: null,
      name: categoryName,
    })),
  )
  return workspace
}

/** Creates "Família Teste" with its seed and returns the membership. Every test with a family starts here. */
export function seedMockFamily(): MeWorkspace {
  return createFamily('Família Teste', 'America/Fortaleza')
}
