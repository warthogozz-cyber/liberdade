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
  /** The family's name for a link whose family the signed-in person is not in (a test's other family). */
  workspaceName?: string
  /** The member a RECOVERY link brings back; absent on an INVITE. */
  targetMembershipId?: string
}

/** A consent as the API lists it, plus whether the signed-in person is the one who gave it. */
export type MockConsent = Schemas['ConsentView'] & { grantedByMe: boolean }

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
  /** The signed-in person's own passkeys, newest first. */
  passkeys: Schemas['PasskeyView'][]
  /** Whether the person also signs in with Google; without it, the last passkey cannot be removed. */
  hasGoogle: boolean
  /** The consents the signed-in person gave or received, newest first. */
  consents: MockConsent[]
  /** The versions of the terms and the privacy policy in force. */
  currentDocuments: Record<'TERMS' | 'PRIVACY', string>
  /** The versions the signed-in person accepted; null while a document is still pending. */
  acceptedDocuments: Record<'TERMS' | 'PRIVACY', { version: string; at: string } | null>
  /** The paid plan: `plan` is FREE until a payment, and `orders` are the checkouts started, newest last. */
  billing: { plan: MockPlan; orders: MockOrder[] }
}

/** The family's plan as the mock keeps it; `endsAt` is when it stops. */
export interface MockPlan {
  key: 'FREE' | 'FAMILY'
  endsAt: string | null
  /** The Founder theme the owner picked; TEAL when none. */
  theme?: Schemas['PlanDetails']['theme']
}

/** One checkout the mock opened. */
export interface MockOrder {
  id: string
  workspaceId: string
  status: 'CREATED' | 'CANCELED' | 'PAID' | 'REFUND_PENDING'
  checkoutUrl: string
}

/** The versions in force, as LegalDocuments.java picks them with billing on (as in staging and the e2e). */
const CURRENT_DOCUMENTS: MockState['currentDocuments'] = {
  TERMS: 'terms-2026-12',
  PRIVACY: 'privacy-2026-12',
}

/** The person starts with both documents accepted, so previews open straight into the app. */
function acceptedDocuments(): MockState['acceptedDocuments'] {
  const at = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
  return {
    TERMS: { version: CURRENT_DOCUMENTS.TERMS, at },
    PRIVACY: { version: CURRENT_DOCUMENTS.PRIVACY, at },
  }
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

/** Two passkeys, so the list shows a removal that is allowed. */
function initialPasskeys(): Schemas['PasskeyView'][] {
  const daysAgo = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString()
  return [
    {
      id: 'mock-passkey-notebook',
      label: 'Notebook',
      createdAt: daysAgo(3),
      lastUsedAt: daysAgo(1),
    },
    { id: 'mock-passkey-celular', label: 'Celular', createdAt: daysAgo(20), lastUsedAt: null },
  ]
}

/** Ana vouched for a youth: she can take the consent back. */
function initialConsents(): MockConsent[] {
  return [
    {
      id: '0192f3a4-0000-7000-8000-0000000000d1',
      purpose: 'PARENTAL',
      documentVersion: 'parental-2026-11',
      workspaceId: MOCK_WORKSPACE_ID,
      workspaceName: 'Família Teste',
      subjectName: 'Leo',
      // The family nickname, as the API returns it; Ana has none here.
      grantedByName: null,
      grantedAt: new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString(),
      revokedAt: null,
      supersededAt: null,
      canRevoke: true,
      grantedByMe: true,
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
      deletionScheduledFor: null,
    },
    suggestedWorkspaceName: 'Família Teste',
    workspaces: [],
    accounts: [],
    categories: [],
    transactions: [],
    deletedTransactionIds: new Set(),
    members: {},
    invitations: [],
    billing: { plan: { key: 'FREE', endsAt: null }, orders: [] },
    modules: {},
    activity: initialActivity(),
    passkeys: initialPasskeys(),
    hasGoogle: true,
    consents: initialConsents(),
    currentDocuments: { ...CURRENT_DOCUMENTS },
    acceptedDocuments: acceptedDocuments(),
  }
}

/** The mock API's data: the session is open, for Ana Teste, still without a family. */
export const mockState: MockState = initialState()

export function resetMockData(): void {
  Object.assign(mockState, initialState())
}

/** The `pendingDocuments` scenario: the person has accepted neither document, so the acceptance screen shows. */
export function seedPendingDocuments(): void {
  mockState.acceptedDocuments = { TERMS: null, PRIVACY: null }
}

/** Seeds a pending INVITE link to another family, as an Owner of it would have made. */
export function seedMockInvitation({
  token,
  workspaceId = 'ws-b',
  workspaceName = 'Família Bia Teste',
  nickname = 'Convidada',
  role = 'ADULT',
  createdByName = 'Bia Teste',
  expiresInMs = 7 * 24 * 60 * 60 * 1000,
  kind = 'INVITE',
  targetMembershipId,
}: {
  token: string
  workspaceId?: string
  workspaceName?: string
  nickname?: string | null
  role?: Schemas['InvitationView']['role']
  createdByName?: string | null
  expiresInMs?: number
  kind?: Schemas['InvitationView']['kind']
  targetMembershipId?: string
}): MockInvitation {
  const invitation: MockInvitation = {
    view: {
      id: crypto.randomUUID(),
      kind,
      nickname,
      role,
      createdByName,
      expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
      token: null,
    },
    token,
    status: 'PENDING',
    workspaceId,
    workspaceName,
    ...(targetMembershipId === undefined ? {} : { targetMembershipId }),
  }
  mockState.invitations.push(invitation)
  return invitation
}

/** Creates a family as `POST /v1/workspaces` does: with the wallet and the default categories. */
export function createFamily(name: string, timezone: string): MeWorkspace {
  const workspace: MeWorkspace = {
    id: crypto.randomUUID(),
    membershipId: crypto.randomUUID(),
    name,
    role: 'OWNER',
    currency: 'BRL',
    country: 'BR',
    locale: 'pt-BR',
    paymentMethods: ['PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH'],
    timezone,
    gettingStartedDismissedAt: null,
    hasTransactions: false,
    familyInvited: false,
    recoveredAt: null,
    deletionScheduledFor: null,
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
