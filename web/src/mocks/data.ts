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

/** A recurring rule as stored: an archived one stays, out of the lists. */
export type MockRule = Schemas['RecurringRuleView'] & { archived: boolean }

/** A monthly limit as stored: the month is `YYYY-MM`, the limit in cents. */
export interface MockBudget {
  month: string
  categoryId: string
  limitCents: bigint
  version: number
}

/**
 * A goal as stored: the target in cents, the due month as `YYYY-MM`. An archived one stays, out of the list; its
 * contributions are newest first.
 */
export interface MockGoal {
  id: string
  name: string
  icon: string
  targetCents: bigint
  currency: string
  dueOn: string | null
  linkedAccountId: string | null
  achievedAt: string | null
  archived: boolean
  version: number
  contributions: Schemas['ContributionView'][]
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
  /** The family's tags, made by naming them on a transaction. */
  tags: Schemas['TagView'][]
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
  /** The family's recurring rules, in creation order, archived ones included. */
  rules: MockRule[]
  /** The family's monthly limits per category. */
  budgets: MockBudget[]
  /** The family's goals, in creation order, archived ones included. */
  goals: MockGoal[]
  /** The family's projects (ventures) by workspace id, in creation order. */
  ventures: Record<string, Schemas['VentureView'][]>
  /** The emergency reserve settings: the target in months and the monthly cost the family typed in. */
  reserve: { targetMonths: number; manualMonthlyCost: Schemas['ReserveView']['manualMonthlyCost'] }
  /** The family's average monthly spending over its history, in cents; null while it has none. */
  spendingHistoryCents: bigint | null
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
  /** The five Banco Central indicators the route returns, in card order (the fake source's values). */
  indicators: Schemas['IndicatorView'][]
  /** What the streak route returns for the signed-in person. */
  streak: Schemas['StreakView']
  /** What the achievements route returns, already in the API's order and filtered for the role. */
  achievements: Schemas['AchievementView'][]
  /**
   * The paid plan: `plan` is FREE until a payment, and `orders` are the checkouts started, newest last.
   * `selling` is billing being on (the legal set in force, as in staging and the e2e); with it off, the
   * API opens the paid features to every family, so the free plan imports too.
   */
  billing: { plan: MockPlan; orders: MockOrder[]; selling: boolean }
  /** The import keys the statement import already took in this session, each line at most once. */
  importKeys: Set<string>
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
  PRIVACY: 'privacy-2026-12.4',
}

/** `LegalDocuments.KEEPS_ACCEPTANCE`: a version keeps the acceptance of the one it continues. */
export const KEEPS_ACCEPTANCE: Record<string, string> = {
  'privacy-2026-11.1': 'privacy-2026-11',
  'privacy-2026-11.2': 'privacy-2026-11.1',
  'privacy-2026-12.1': 'privacy-2026-12',
  'privacy-2026-11.3': 'privacy-2026-11.2',
  'privacy-2026-12.2': 'privacy-2026-12.1',
  'privacy-2026-12.3': 'privacy-2026-12.2',
  'privacy-2026-11.4': 'privacy-2026-11.3',
  'privacy-2026-12.4': 'privacy-2026-12.3',
}

/** Whether `accepted` is `current` or a version `current` continues, down the chain (`LegalDocuments.KEEPS_ACCEPTANCE`). */
export function keepsAcceptance(current: string, accepted: string | undefined): boolean {
  let version: string | undefined = current
  while (version !== undefined) {
    if (version === accepted) return true
    version = KEEPS_ACCEPTANCE[version]
  }
  return false
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

function initialIndicators(): Schemas['IndicatorView'][] {
  const source = { name: 'Banco Central do Brasil', url: 'https://api.bcb.gov.br/dados/serie' }
  const updatedAt = '2026-11-24T11:30:02Z'
  const base = { source, updatedAt, stale: false }
  return [
    {
      ...base,
      code: 'SELIC_TARGET',
      name: 'Selic (meta)',
      unit: 'PERCENT_YEAR',
      value: '15.00',
      referenceDate: '2026-11-24',
      variation: { value: '0.25', unit: 'PP', basis: 'LAST_CHANGE', since: '2026-11-05' },
    },
    {
      ...base,
      code: 'CDI',
      name: 'CDI',
      unit: 'PERCENT_YEAR',
      value: '14.90',
      referenceDate: '2026-11-24',
      variation: { value: '0.00', unit: 'PP', basis: 'PREVIOUS', since: '2026-11-21' },
    },
    {
      ...base,
      code: 'IPCA_MONTH',
      name: 'IPCA do mês',
      unit: 'PERCENT_MONTH',
      value: '0.50',
      referenceDate: '2026-10-01',
      variation: { value: '0.10', unit: 'PP', basis: 'PREVIOUS', since: '2026-09-01' },
    },
    {
      ...base,
      code: 'IPCA_12M',
      name: 'IPCA em 12 meses',
      unit: 'PERCENT_12M',
      value: '4.00',
      referenceDate: '2026-10-01',
      variation: { value: '-0.10', unit: 'PP', basis: 'PREVIOUS', since: '2026-09-01' },
    },
    {
      ...base,
      code: 'USD_PTAX_SELL',
      name: 'Dólar (PTAX venda)',
      unit: 'BRL',
      value: '5.4100',
      referenceDate: '2026-11-24',
      variation: { value: '-0.41', unit: 'PERCENT', basis: 'PREVIOUS', since: '2026-11-21' },
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
    tags: [],
    transactions: [],
    deletedTransactionIds: new Set(),
    members: {},
    invitations: [],
    billing: { plan: { key: 'FREE', endsAt: null }, orders: [], selling: true },
    importKeys: new Set(),
    indicators: initialIndicators(),
    streak: { weeks: 0, frozenWeeks: 0, longest: 0 },
    achievements: [],
    modules: {},
    rules: [],
    budgets: [],
    goals: [],
    ventures: {},
    reserve: { targetMonths: 6, manualMonthlyCost: null },
    spendingHistoryCents: null,
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
    isReserve: false,
  })
  mockState.categories.push(
    ...EXPENSE_CATEGORIES.map(([icon, categoryName]) => ({
      id: crypto.randomUUID(),
      kind: 'EXPENSE' as const,
      icon,
      color: null,
      parentId: null,
      archivedAt: null,
      isFixed: false,
      name: categoryName,
    })),
    ...INCOME_CATEGORIES.map(([icon, categoryName]) => ({
      id: crypto.randomUUID(),
      kind: 'INCOME' as const,
      icon,
      color: null,
      parentId: null,
      archivedAt: null,
      isFixed: false,
      name: categoryName,
    })),
  )
  return workspace
}

/** Creates "Família Teste" with its seed and returns the membership. Every test with a family starts here. */
export function seedMockFamily(): MeWorkspace {
  return createFamily('Família Teste', 'America/Fortaleza')
}

/** Puts the family on the paid plan, until a year from the mock's dates, as a paid checkout would. */
export function seedFamilyPlan(): void {
  mockState.billing.plan = { key: 'FAMILY', endsAt: '2027-11-30T03:00:00Z' }
}

const brl = (amount: string): Schemas['Money'] => ({ amount, currency: 'BRL' })

/** The `food-sweets` project as the API shows it: R$ 540,00 a month, paid back in 2 months. */
export function foodSweetsVenture(
  overrides: Partial<Schemas['VentureView']> = {},
): Schemas['VentureView'] {
  return {
    id: '0192f3a4-0000-7000-8000-0000000000a1',
    name: 'Doces caseiros',
    type: 'FOOD',
    templateKey: 'food-sweets',
    status: 'ACTIVE',
    startedOn: '2026-10-01',
    closedOn: null,
    currency: 'BRL',
    version: 0,
    initialItems: [
      {
        id: '0192f3a4-0000-7000-8000-0000000000b1',
        name: 'Forma e utensílios',
        amount: brl('600.00'),
        isExample: false,
      },
    ],
    fixedItems: [
      {
        id: '0192f3a4-0000-7000-8000-0000000000b2',
        name: 'Embalagens',
        amount: brl('60.00'),
        isExample: false,
      },
    ],
    products: [
      {
        id: '0192f3a4-0000-7000-8000-0000000000c1',
        name: 'Brigadeiro',
        unitPrice: brl('4.50'),
        unitVariableCost: brl('1.80'),
        plannedUnitsMonth: 200,
        isExample: false,
      },
    ],
    steps: [
      {
        id: '0192f3a4-0000-7000-8000-0000000000d1',
        title: 'Testar a receita',
        position: 0,
        doneAt: null,
      },
    ],
    figures: {
      initialInvestment: brl('600.00'),
      fixedMonthly: brl('60.00'),
      plannedRevenue: brl('900.00'),
      plannedCost: brl('360.00'),
      totalMargin: brl('540.00'),
      monthlyProfit: brl('540.00'),
      paybackMonths: 2,
      paybackState: 'MONTHS',
      roi12mPercent: '980.0',
      roiState: 'OK',
      breakEven: { state: 'OK', revenue: brl('150.00'), weightedMarginPercent: '60.0' },
      products: [
        { margin: brl('2.70'), marginPercent: '60.0', priceCoversCost: true, breakEvenUnits: 23 },
      ],
    },
    ...overrides,
  }
}

/**
 * The figures of the PRD's example, what the simulation route answers: R$ 2,70 a unit at 60,0 %,
 * break-even at 100 units (R$ 450,00), R$ 540,00 a month, paid back in 2 months, 710,0 % in a year.
 */
export function prdExampleFigures(): Schemas['FiguresView'] {
  return {
    initialInvestment: brl('800.00'),
    fixedMonthly: brl('270.00'),
    plannedRevenue: brl('1350.00'),
    plannedCost: brl('540.00'),
    totalMargin: brl('810.00'),
    monthlyProfit: brl('540.00'),
    paybackMonths: 2,
    paybackState: 'MONTHS',
    roi12mPercent: '710.0',
    roiState: 'OK',
    breakEven: { state: 'OK', revenue: brl('450.00'), weightedMarginPercent: '60.0' },
    products: [
      { margin: brl('2.70'), marginPercent: '60.0', priceCoversCost: true, breakEvenUnits: 100 },
    ],
  }
}

function ventureTemplate(
  key: string,
  name: string,
  type: Schemas['TemplateView']['type'],
): Schemas['TemplateView'] {
  const venture = foodSweetsVenture()
  return {
    key,
    name,
    type,
    figures: venture.figures,
    initialItems: venture.initialItems.map(({ name: item, amount }) => ({ name: item, amount })),
    fixedItems: venture.fixedItems.map(({ name: item, amount }) => ({ name: item, amount })),
    products: venture.products.map(
      ({ name: item, unitPrice, unitVariableCost, plannedUnitsMonth }) => ({
        name: item,
        unitPrice,
        unitVariableCost,
        plannedUnitsMonth,
      }),
    ),
    steps: venture.steps.map((step) => step.title),
  }
}

/** The five tracks the API offers, in its order: the last one has no template, it is educational. */
export function ventureTracks(): Schemas['TrackView'][] {
  return [
    {
      key: 'food',
      name: 'Culinária',
      description: 'Venda o que você sabe cozinhar.',
      educational: false,
      templates: [ventureTemplate('food-sweets', 'Doces caseiros', 'FOOD')],
    },
    {
      key: 'digital',
      name: 'Produtos digitais',
      description: 'Crie uma vez e venda várias vezes.',
      educational: false,
      templates: [ventureTemplate('digital-ebook', 'E-book ou curso curto', 'DIGITAL_PRODUCT')],
    },
    {
      key: 'content',
      name: 'Conteúdo',
      description: 'Transforme o que você sabe em vídeos, textos e aulas.',
      educational: false,
      templates: [ventureTemplate('content-channel', 'Canal de conteúdo', 'CONTENT')],
    },
    {
      key: 'resale-services',
      name: 'Revenda e serviços',
      description: 'Revenda produtos ou venda o seu tempo.',
      educational: false,
      templates: [
        ventureTemplate('resale-goods', 'Revenda de produtos', 'RESALE'),
        ventureTemplate('service-hours', 'Serviço por atendimento', 'SERVICE'),
      ],
    },
    {
      key: 'investments',
      name: 'Investimentos (conteúdo educativo)',
      description: 'Entenda como cada opção funciona e simule cenários.',
      educational: true,
      templates: [],
    },
  ]
}

/** Gives the family the `food-sweets` project and turns the module on. */
export function seedVentureFixture(
  workspace: MeWorkspace,
  overrides: Partial<Schemas['VentureView']> = {},
): Schemas['VentureView'] {
  mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], ventures: true }
  const venture = foodSweetsVenture(overrides)
  mockState.ventures[workspace.id] = [...(mockState.ventures[workspace.id] ?? []), venture]
  return venture
}

/**
 * Fills the planning data the screens read, for a family that already has its seed: one limit on
 * Mercado that is near (R$ 400,00 spent of R$ 500,00 this month), two goals (one with a due month),
 * and a spending history, so the reserve takes its cost from it and the projection has an average.
 */
export function seedPlanningFixture(workspace: MeWorkspace): void {
  const month = new Date().toISOString().slice(0, 7)
  const account = mockState.accounts[0]
  const mercado = mockState.categories.find((each) => each.name === 'Mercado')
  if (!account || !mercado) throw new Error('Seed the family before the planning fixture')
  mockState.transactions.push({
    id: crypto.randomUUID(),
    kind: 'EXPENSE',
    amount: { amount: '400.00', currency: workspace.currency },
    occurredOn: `${month}-02`,
    accountId: account.id,
    toAccountId: null,
    categoryId: mercado.id,
    description: null,
    paymentMethod: null,
    tags: [],
    status: 'CONFIRMED',
    scheduledOn: null,
    recurringRuleId: null,
    importKey: null,
    createdAt: new Date().toISOString(),
    memberId: workspace.membershipId,
    version: 0,
  })
  mockState.budgets.push({ month, categoryId: mercado.id, limitCents: 50_000n, version: 0 })
  const dueYear = Number(month.slice(0, 4)) + 1
  mockState.goals.push(
    {
      id: crypto.randomUUID(),
      name: 'Viagem',
      icon: 'travel',
      targetCents: 500_000n,
      currency: workspace.currency,
      dueOn: `${dueYear}-${month.slice(5)}`,
      linkedAccountId: null,
      achievedAt: null,
      archived: false,
      version: 0,
      contributions: [
        {
          id: crypto.randomUUID(),
          amount: { amount: '1000.00', currency: workspace.currency },
          memberId: workspace.membershipId,
          occurredOn: `${month}-01`,
        },
      ],
    },
    {
      id: crypto.randomUUID(),
      name: 'Notebook',
      icon: 'tech',
      targetCents: 300_000n,
      currency: workspace.currency,
      dueOn: null,
      linkedAccountId: null,
      achievedAt: null,
      archived: false,
      version: 0,
      contributions: [],
    },
  )
  mockState.spendingHistoryCents = 300_000n
}
