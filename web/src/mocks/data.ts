import type { MeUser, MeWorkspace, TransactionView } from '@/shared/api'
import type { components } from '@/shared/api/schema'

type Schemas = components['schemas']

/** An account as stored; its balance is worked out from the transactions. */
export type MockAccount = Omit<Schemas['AccountView'], 'balance'>

/** A link as stored: the secret stays here, and only the answer that creates it shows it. */
export interface MockInvitation {
  view: Schemas['InvitationView']
  token: string
  status: 'PENDING' | 'USED' | 'REVOKED'
  workspaceId: string
  /** The member a RECOVERY link brings back; absent on an INVITE. */
  targetMembershipId?: string
}

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
  /** The family's people by workspace id, in list order; the caller's own row is the one whose id is the membershipId. */
  members: Record<string, Schemas['MemberView'][]>
  /** Every link made, whatever became of it, in creation order. */
  invitations: MockInvitation[]
  /** The modules each family switched on, by workspace id. A module with no entry is off. */
  modules: Record<string, Partial<Record<Schemas['ModuleView']['key'], boolean>>>
  /** The signed-in person's own history, newest first (LGPD audit trail). */
  activity: Schemas['ActivityItem'][]
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

const MOCK_WORKSPACE_ID = '0192f3a4-0000-7000-8000-0000000000a1'

/** A few realistic lines: what Ana did, and what was done to her account. */
function initialActivity(): Schemas['ActivityItem'][] {
  const hoursAgo = (hours: number) => new Date(Date.now() - hours * 60 * 60 * 1000).toISOString()
  return [
    {
      id: '0192f3a4-0000-7000-8000-0000000000c3',
      action: 'auth.signed_in',
      occurredAt: hoursAgo(1),
      workspaceId: null,
      actorName: 'Ana Teste',
      byMe: true,
      metadata: { method: 'PASSKEY', browser: 'Chrome', os: 'Android' },
    },
    {
      id: '0192f3a4-0000-7000-8000-0000000000c2',
      action: 'member.role_changed',
      occurredAt: hoursAgo(30),
      workspaceId: MOCK_WORKSPACE_ID,
      actorName: 'Bia',
      byMe: false,
      metadata: { from: 'VIEWER', to: 'ADULT' },
    },
    {
      id: '0192f3a4-0000-7000-8000-0000000000c1',
      action: 'invitation.created',
      occurredAt: hoursAgo(72),
      workspaceId: MOCK_WORKSPACE_ID,
      actorName: 'Ana Teste',
      byMe: true,
      metadata: { role: 'ADULT' },
    },
  ]
}

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
    members: {},
    invitations: [],
    modules: {},
    activity: initialActivity(),
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
    gettingStartedDismissedAt: null,
    hasTransactions: false,
    familyInvited: false,
    recoveredAt: null,
  }
  mockState.workspaces.push(workspace)
  mockState.members[workspace.id] = [
    {
      id: workspace.membershipId,
      displayName: mockState.user.displayName,
      nickname: null,
      role: 'OWNER',
      status: 'ACTIVE',
      you: true,
      passkeyOnly: true,
      joinedAt: new Date().toISOString(),
    },
  ]
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
