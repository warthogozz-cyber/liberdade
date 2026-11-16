import { http, HttpResponse } from 'msw'
import {
  API_URL,
  type ApiMoney,
  type CreateTransactionRequest,
  type MeWorkspace,
  type TransactionView,
} from '@/shared/api'
import { centsToApi, MAX_CENTS, parseCents } from '@/shared/money'
import type { components } from '@/shared/api/schema'
import { createFamily, type MockAccount, type MockInvitation, mockState } from './data'

type Schemas = components['schemas']

const CSRF = { headerName: 'X-XSRF-TOKEN', token: 'mock-csrf' }
const MAX_NAME_LENGTH = 60
const MAX_DESCRIPTION_LENGTH = 140
const DEFAULT_TIMEZONE = 'America/Fortaleza'
const DAY = 24 * 60 * 60 * 1000
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const UUID_V7 = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const DATE = /^\d{4}-\d{2}-\d{2}$/
const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/
const NUMBER = /^-?\d+(\.\d+)?$/
const DECIMAL = /^\d+(\.\d+)?$/
const KINDS: unknown[] = ['INCOME', 'EXPENSE', 'TRANSFER']
const ACCOUNT_TYPES: unknown[] = [
  'CHECKING',
  'SAVINGS',
  'CASH',
  'CREDIT_CARD',
  'WALLET',
  'INVESTMENT',
]
const ACCOUNT_AMOUNT = /^-?\d+(\.\d{1,2})?$/
const CHALLENGE = 'bW9jay1jaGFsbGVuZ2U'
const csrfInvalid = () => problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
const authRequired = () => problem(401, 'auth.required', 'Entre para continuar.')

interface FieldError {
  field: string
  code: string
}

/** A problem answer, shaped like the API's (`application/problem+json`), with any extra properties. */
/** 43 base64url characters, like the API's tokens (32 random bytes). */
function newToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '')
}

const INVITE_ROLES: unknown[] = ['OWNER', 'ADULT', 'YOUTH', 'VIEWER']
const roleDenied = () => problem(403, 'auth.forbidden', 'Você não tem permissão para isso.')
const invitationNotFound = () => problem(404, 'invitation.not-found', 'Convite não encontrado.')
const invitationGoneProblem = () =>
  problem(410, 'invitation.gone', 'Este convite já foi usado, cancelado ou venceu.')

const MODULE_KEYS: Schemas['ModuleView']['key'][] = [
  'planning',
  'indicators',
  'gamification',
  'ventures',
]

/** The four modules in the API's order, off unless the family switched them on. */
function moduleViews(workspaceId: string): Schemas['ModuleView'][] {
  const on = mockState.modules[workspaceId] ?? {}
  return MODULE_KEYS.map((key) => ({ key, enabled: on[key] === true }))
}

/** A link is gone once used, cancelled or past its date. */
function invitationGone(invitation: MockInvitation): boolean {
  return invitation.status !== 'PENDING' || Date.parse(invitation.view.expiresAt) <= Date.now()
}

/** Puts the family in the signed-in person's `me`, as joining it does, when it is not there yet. */
function joinWorkspace(
  invitation: MockInvitation,
  membershipId: string,
  role: Schemas['MemberView']['role'],
): void {
  if (mockState.workspaces.some((workspace) => workspace.id === invitation.workspaceId)) return
  mockState.workspaces.push({
    id: invitation.workspaceId,
    membershipId,
    name: invitation.workspaceName ?? '',
    role,
    currency: 'BRL',
    timezone: 'America/Fortaleza',
    gettingStartedDismissedAt: null,
    hasTransactions: false,
    familyInvited: false,
    recoveredAt: null,
    deletionScheduledFor: null,
  })
}

/** The member a RECOVERY link is for. */
function recoveryTargetOf(invitation: MockInvitation) {
  return mockState.members[invitation.workspaceId]?.find(
    (member) => member.id === invitation.targetMembershipId,
  )
}

function lastOwner() {
  return problem(422, 'member.last-owner', 'A família precisa de pelo menos uma Dona ou Dono.')
}

export function problem(
  status: number,
  code: string,
  detail?: string,
  errors?: FieldError[],
  properties: Record<string, unknown> = {},
) {
  return HttpResponse.json(
    { ...properties, status, detail, code, ...(errors && { errors }) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

const malformed = () => problem(400, 'request.malformed', 'Não conseguimos ler os dados enviados.')
const invalid = (errors: FieldError[]) =>
  problem(422, 'request.invalid', 'Alguns campos precisam de ajuste.', errors)
const rejected = (code: string, detail: string) => problem(422, code, detail)

const url = (path: string) => `${API_URL}${path}`

// An empty but valid zip: just the end-of-central-directory record.
const EMPTY_ZIP = new Uint8Array([0x50, 0x4b, 0x05, 0x06, ...new Array<number>(18).fill(0)])

function zipResponse(prefix: string) {
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Fortaleza' }).format(new Date())
  return new HttpResponse(EMPTY_ZIP, {
    status: 200,
    headers: {
      'Content-Type': 'application/zip',
      'Content-Disposition': `attachment; filename="${prefix}-${day}.zip"`,
      'Cache-Control': 'no-store',
    },
  })
}

/** The API answers 403 `csrf.invalid` to any write without the token. */
function withoutToken(request: Request) {
  return request.headers.get(CSRF.headerName) !== CSRF.token
}

/**
 * The signed-in user, then their family `ws`, guard every route under a family. A family being deleted is hidden
 * (404) like the API does, except to its Owners on the two routes that opt in.
 */
function guard(ws: string, includePendingDeletion = false): MeWorkspace | Response {
  if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
  const workspace = mockState.workspaces.find((candidate) => candidate.id === ws)
  const visible =
    workspace &&
    (!workspace.deletionScheduledFor || (includePendingDeletion && workspace.role === 'OWNER'))
  return visible ? workspace : problem(404, 'workspace.not-found', 'Família não encontrada.')
}

async function readJson(request: Request): Promise<unknown> {
  try {
    return (await request.json()) as unknown
  } catch {
    return undefined
  }
}

function money(cents: bigint, currency: string): ApiMoney {
  return { amount: centsToApi(cents), currency }
}

/** The transactions not deleted: the only ones lists and sums see. */
function activeTransactions(): TransactionView[] {
  return mockState.transactions.filter(
    (transaction) => !mockState.deletedTransactionIds.has(transaction.id),
  )
}

/** What the transaction moved in the account: income in, expenses out, a transfer out of one and into the other. */
function signedCents(transaction: TransactionView, accountId: string): bigint {
  const cents = parseCents(transaction.amount.amount)
  if (transaction.kind === 'INCOME') return transaction.accountId === accountId ? cents : 0n
  if (transaction.kind === 'EXPENSE') return transaction.accountId === accountId ? -cents : 0n
  return (
    (transaction.toAccountId === accountId ? cents : 0n) -
    (transaction.accountId === accountId ? cents : 0n)
  )
}

function movementCents(accountId: string): bigint {
  return activeTransactions().reduce(
    (sum, transaction) => sum + signedCents(transaction, accountId),
    0n,
  )
}

/** The initial balance plus what the transactions moved, as the API computes it. */
function balanceCents(account: MockAccount): bigint {
  return parseCents(account.initialBalance.amount) + movementCents(account.id)
}

/** An account as the API lists it, with its computed balance. */
function accountView(account: MockAccount) {
  return { ...account, balance: money(balanceCents(account), account.currency) }
}

/** Active accounts first, then the archived, each by name and id, as the API lists them. */
function activeFirst(a: MockAccount, b: MockAccount) {
  return (
    Number(a.archivedAt !== null) - Number(b.archivedAt !== null) ||
    a.name.localeCompare(b.name) ||
    a.id.localeCompare(b.id)
  )
}

type CategoryKindName = Schemas['CategoryView']['kind']
type CategoryColor = NonNullable<Schemas['CategoryView']['color']>
const MAX_CATEGORY_NAME_LENGTH = 40
const MAX_ICON_LENGTH = 16
const CATEGORY_COLORS: unknown[] = [
  'TEAL',
  'BLUE',
  'INDIGO',
  'PURPLE',
  'PINK',
  'RED',
  'ORANGE',
  'GREEN',
]
const categoryNotFound = () => problem(404, 'category.not-found', 'Categoria não encontrada.')
const categoryNameTaken = () =>
  problem(409, 'category.name-taken', 'Já existe uma categoria com esse nome aqui.')

/** Two active categories of a kind never share a name under one parent (D30). */
function categoryNameInUse(
  name: string,
  kind: CategoryKindName,
  parentId: string | null,
  exceptId?: string,
) {
  return mockState.categories.some(
    (category) =>
      category.archivedAt === null &&
      category.kind === kind &&
      category.parentId === parentId &&
      category.name === name &&
      category.id !== exceptId,
  )
}

const accountNotFound = () => problem(404, 'account.not-found', 'Conta não encontrada.')
const nameTaken = () => problem(409, 'account.name-taken', 'Já existe uma conta com esse nome.')
const currencyUnsupported = () =>
  rejected('account.currency-unsupported', 'Por enquanto, as contas usam a moeda da família.')

function nameInUse(name: string, exceptId?: string) {
  return mockState.accounts.some(
    (account) => account.archivedAt === null && account.name === name && account.id !== exceptId,
  )
}

/** Whether the cents are within 999,999,999.99 in absolute value; negative and zero pass, as `@BalanceAmount`. */
function withinBalanceLimit(cents: bigint): boolean {
  return cents >= -MAX_CENTS && cents <= MAX_CENTS
}

interface ReadMoney {
  cents: bigint
  currency: string
}

/**
 * Reads a money field of an account body. `undefined` is a body the API cannot read (a 400): not an object, an
 * amount that is not a string with a plain decimal of at most 2 places, or a currency that is not text.
 * `null` is a missing value.
 */
function readMoney(value: unknown): ReadMoney | null | undefined {
  if (value == null) return null
  if (typeof value !== 'object') return undefined
  const { amount, currency } = value as { amount?: unknown; currency?: unknown }
  if (typeof amount !== 'string' || typeof currency !== 'string') return undefined
  if (!ACCOUNT_AMOUNT.test(amount)) return undefined
  return { cents: parseCents(amount), currency }
}

function validName(name: string): boolean {
  return name.length >= 1 && name.length <= MAX_NAME_LENGTH && !name.includes('\u0000')
}

/** Above zero, at most 999,999,999.99 and with at most 2 decimal places, as the API checks it. */
function validCents(amount: string): bigint | undefined {
  if (!DECIMAL.test(amount)) return undefined
  const [whole, fraction = ''] = amount.split('.')
  const decimals = fraction.replace(/0+$/, '')
  if (decimals.length > 2) return undefined
  const cents = parseCents(decimals ? `${whole}.${decimals}` : whole)
  return cents > 0n && cents <= MAX_CENTS ? cents : undefined
}

type UpdateTransactionRequest = Schemas['UpdateTransactionRequest']

/** What the API cannot even read is a 400, not a 422: a bad UUID, date, kind, number or version. */
function readable(body: Partial<CreateTransactionRequest & UpdateTransactionRequest>) {
  const isUuid = (value: unknown) =>
    value == null || (typeof value === 'string' && UUID.test(value))
  return (
    [body.id, body.accountId, body.toAccountId, body.categoryId, body.memberId].every(isUuid) &&
    (body.kind == null || KINDS.includes(body.kind)) &&
    (body.occurredOn == null || DATE.test(body.occurredOn)) &&
    (body.amount?.amount == null || NUMBER.test(String(body.amount.amount))) &&
    (body.version == null || Number.isInteger(body.version))
  )
}

const CREATE_REQUIRED = ['id', 'kind', 'amount', 'occurredOn', 'accountId'] as const
const UPDATE_REQUIRED = ['version', 'kind', 'amount', 'occurredOn', 'accountId'] as const

/** The body checks of a new or edited transaction; the description comes back stripped. */
function validateTransaction(
  body: Partial<CreateTransactionRequest & UpdateTransactionRequest>,
  required: readonly (keyof CreateTransactionRequest | keyof UpdateTransactionRequest)[],
) {
  const errors: FieldError[] = required
    .filter((field) => body[field] == null)
    .map((field) => ({
      field,
      code: 'NotNull',
    }))
  const cents = body.amount == null ? undefined : validCents(String(body.amount.amount))
  if (body.amount != null && cents === undefined) {
    errors.push({ field: 'amount', code: 'TransactionAmount' })
  }
  const description = body.description?.trim() || null
  if (description !== null && description.length > MAX_DESCRIPTION_LENGTH) {
    errors.push({ field: 'description', code: 'Size' })
  }
  if (description?.includes('\u0000')) errors.push({ field: 'description', code: 'Pattern' })
  return { errors, cents: cents ?? 0n, description }
}

function sameContent(stored: TransactionView, request: TransactionView) {
  return (
    stored.kind === request.kind &&
    stored.amount.currency === request.amount.currency &&
    stored.amount.amount === request.amount.amount &&
    stored.occurredOn === request.occurredOn &&
    stored.accountId === request.accountId &&
    stored.toAccountId === request.toAccountId &&
    stored.categoryId === request.categoryId &&
    stored.memberId === request.memberId &&
    stored.description === request.description
  )
}

const MIN_DATE = '2000-01-01'
const DAYS_AHEAD = 366
const transactionNotFound = () =>
  problem(404, 'transaction.not-found', 'Lançamento não encontrado.')

/** The family's date now, in its own time zone, as `YYYY-MM-DD`. */
function familyToday(workspace: MeWorkspace): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: workspace.timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

/** The last day a transaction may fall on: 366 days after the family's today. */
function lastDay(workspace: MeWorkspace): string {
  const today = Date.parse(`${familyToday(workspace)}T00:00:00Z`)
  return new Date(today + DAYS_AHEAD * DAY).toISOString().slice(0, 10)
}

/** The panel shows the biggest six categories and gathers the rest in "Outras" (D8). */
const CATEGORY_LIMIT = 6

/** A category's top-level id: a subcategory counts for its parent (D9). */
function topCategoryId(categoryId: string): string {
  const category = mockState.categories.find((each) => each.id === categoryId)
  return category?.parentId ?? categoryId
}

/** The month's spending by top-level category, this month and last, ranked as the API ranks it. */
function categoriesFor(month: string, currency: string) {
  const [year, number] = month.split('-').map(Number)
  const previousMonth = new Date(Date.UTC(year, number - 2, 1)).toISOString().slice(0, 7)
  const totals = new Map<string, { current: bigint; previous: bigint }>()
  for (const transaction of activeTransactions()) {
    if (transaction.kind !== 'EXPENSE' || transaction.categoryId === null) continue
    const current = transaction.occurredOn.startsWith(`${month}-`)
    if (!current && !transaction.occurredOn.startsWith(`${previousMonth}-`)) continue
    const id = topCategoryId(transaction.categoryId)
    const total = totals.get(id) ?? { current: 0n, previous: 0n }
    const cents = parseCents(transaction.amount.amount)
    if (current) total.current += cents
    else total.previous += cents
    totals.set(id, total)
  }
  const ranked = [...totals.entries()]
    .filter(([, total]) => total.current !== 0n || total.previous !== 0n)
    .sort(([idA, a], [idB, b]) => {
      if (a.current !== b.current) return a.current > b.current ? -1 : 1
      if (a.previous !== b.previous) return a.previous > b.previous ? -1 : 1
      return idA.localeCompare(idB)
    })
  const line = (categoryId: string | null, total: { current: bigint; previous: bigint }) => ({
    categoryId,
    current: money(total.current, currency),
    previous: money(total.previous, currency),
  })
  const lines = ranked.slice(0, CATEGORY_LIMIT).map(([id, total]) => line(id, total))
  const rest = ranked.slice(CATEGORY_LIMIT)
  if (rest.length > 0) {
    lines.push(
      line(null, {
        current: rest.reduce((sum, [, total]) => sum + total.current, 0n),
        previous: rest.reduce((sum, [, total]) => sum + total.previous, 0n),
      }),
    )
  }
  return lines
}

/** The month at a glance, computed as the API does: the balance of the active accounts at each day's end. */
function dashboardFor(month: string, currency: string) {
  const [year, number] = month.split('-').map(Number)
  const days = new Date(Date.UTC(year, number, 0)).getUTCDate()
  const transactions = activeTransactions().filter(
    (transaction) => transaction.occurredOn <= `${month}-${String(days).padStart(2, '0')}`,
  )
  const active = mockState.accounts.filter((account) => account.archivedAt === null)
  let income = 0n
  let expenses = 0n
  const moved = new Array<bigint>(days).fill(0n)
  let balance = active.reduce((sum, account) => sum + parseCents(account.initialBalance.amount), 0n)
  for (const transaction of transactions) {
    const effect = active.reduce((sum, account) => sum + signedCents(transaction, account.id), 0n)
    if (transaction.occurredOn < `${month}-01`) {
      balance += effect
      continue
    }
    moved[Number(transaction.occurredOn.slice(8, 10)) - 1] += effect
    if (transaction.kind === 'INCOME') income += parseCents(transaction.amount.amount)
    if (transaction.kind === 'EXPENSE') expenses += parseCents(transaction.amount.amount)
  }
  const dailyBalance = moved.map((effect, index) => {
    balance += effect
    return {
      day: `${month}-${String(index + 1).padStart(2, '0')}`,
      balance: money(balance, currency),
    }
  })
  return {
    month,
    income: money(income, currency),
    expenses: money(expenses, currency),
    result: money(income - expenses, currency),
    balance: money(balance, currency),
    byCategory: categoriesFor(month, currency),
    dailyBalance,
  }
}

/** The list and the dashboard show January 2000 up to the month of the last day; others are a 422. */
function monthOutOfRange(workspace: MeWorkspace, month: string): Response | undefined {
  if (month < MIN_DATE.slice(0, 7) || month > lastDay(workspace).slice(0, 7)) {
    return rejected('month.out-of-range', 'Escolha um mês entre janeiro de 2000 e o próximo ano.')
  }
  return undefined
}

interface Proposed {
  kind: TransactionView['kind']
  amount: ApiMoney
  occurredOn: string
  accountId: string
  toAccountId: string | null
  categoryId: string | null
  memberId: string
}

/** Whether the person is an active member of the family. */
function activeMember(workspace: MeWorkspace, membershipId: string) {
  return (mockState.members[workspace.id] ?? []).some(
    (member) => member.id === membershipId && member.status === 'ACTIVE',
  )
}

/** An account the transaction may use: active, or the one it already had (D10). */
function usableAccount(accountId: string, keptId: string | null | undefined) {
  return mockState.accounts.find(
    (account) => account.id === accountId && (account.archivedAt === null || account.id === keptId),
  )
}

/**
 * The family rules of the API, in its order, the first failure deciding. On an edit, `current` is
 * the stored transaction: what was archived since may stay, but cannot be chosen anew.
 */
function familyRuleFailure(
  workspace: MeWorkspace,
  proposed: Proposed,
  current?: TransactionView,
): Response | undefined {
  if (proposed.occurredOn < MIN_DATE || proposed.occurredOn > lastDay(workspace)) {
    return rejected('transaction.date-out-of-range', 'Escolha uma data entre 2000 e o próximo ano.')
  }
  const { toAccountId, categoryId } = proposed
  const transfer = proposed.kind === 'TRANSFER'
  const shapeFits = transfer
    ? toAccountId !== null && toAccountId !== proposed.accountId && categoryId === null
    : toAccountId === null
  if (!shapeFits) {
    return rejected(
      'transaction.transfer-invalid',
      'Uma transferência precisa de duas contas diferentes e não leva categoria.',
    )
  }
  if (!transfer && categoryId === null) {
    return rejected('transaction.category-required', 'Escolha uma categoria.')
  }
  const account = usableAccount(proposed.accountId, current?.accountId)
  const destination =
    toAccountId === null ? account : usableAccount(toAccountId, current?.toAccountId)
  if (!account || !destination) {
    return rejected('transaction.account-not-found', 'Conta não encontrada nesta família.')
  }
  if (categoryId !== null) {
    const category = mockState.categories.find(
      (existing) =>
        existing.id === categoryId &&
        (existing.archivedAt === null || existing.id === current?.categoryId),
    )
    if (!category) {
      return rejected('transaction.category-not-found', 'Categoria não encontrada nesta família.')
    }
    if (category.kind !== proposed.kind) {
      return rejected(
        'transaction.category-kind-mismatch',
        'Esta categoria não combina com o tipo do lançamento.',
      )
    }
  }
  const { currency } = proposed.amount
  if (currency !== account.currency || currency !== destination.currency) {
    return rejected('transaction.currency-mismatch', 'A moeda do valor não é a mesma da conta.')
  }
  // The member the transaction already has may have left since; anyone else must be in the family.
  if (
    proposed.memberId !== workspace.membershipId &&
    proposed.memberId !== current?.memberId &&
    !activeMember(workspace, proposed.memberId)
  ) {
    return rejected('transaction.member-not-found', 'Essa pessoa não faz parte da família.')
  }
  return undefined
}

/** The transaction with its id, as stored; with whether it is deleted. */
function storedTransaction(id: string) {
  const transaction = mockState.transactions.find((existing) => existing.id === id)
  return transaction && { transaction, deleted: mockState.deletedTransactionIds.has(id) }
}

/** Newest first: by date, then by creation, then by id, as the API lists them. */
function newestFirst(a: TransactionView, b: TransactionView) {
  return (
    b.occurredOn.localeCompare(a.occurredOn) ||
    b.createdAt.localeCompare(a.createdAt) ||
    b.id.localeCompare(a.id)
  )
}

function isKnownTimezone(timezone: unknown): timezone is string {
  if (typeof timezone !== 'string') return false
  try {
    new Intl.DateTimeFormat('en', { timeZone: timezone })
    return true
  } catch {
    return false
  }
}

/**
 * The routes of Tasks 3, 5 and 6, with the API's error codes, plus `POST /logout` and the four
 * passkey routes of Task 4.
 */
export const handlers = [
  http.get(url('/v1/csrf'), () => HttpResponse.json(CSRF)),

  http.post(url('/logout'), ({ request }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    return new HttpResponse(null, { status: 204 })
  }),

  // Spring's passkey options, fixed: the relying party is `localhost`, named "Liberdade".
  http.post(url('/webauthn/register/options'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    return HttpResponse.json({
      rp: { id: 'localhost', name: 'Liberdade' },
      user: {
        id: 'bW9jay11c2VyLWhhbmRsZQ',
        name: 'ana@example.com',
        displayName: mockState.user.displayName,
      },
      challenge: CHALLENGE,
      pubKeyCredParams: [
        { type: 'public-key', alg: -8 },
        { type: 'public-key', alg: -7 },
        { type: 'public-key', alg: -257 },
      ],
      timeout: 300000,
      excludeCredentials: [],
      authenticatorSelection: { residentKey: 'required', userVerification: 'preferred' },
      attestation: 'none',
      extensions: { credProps: true },
    })
  }),

  http.post(url('/webauthn/authenticate/options'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    return HttpResponse.json({
      challenge: CHALLENGE,
      timeout: 300000,
      rpId: 'localhost',
      allowCredentials: [],
      userVerification: 'preferred',
      extensions: {},
    })
  }),

  http.post(url('/webauthn/register'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    mockState.user.passkeyCreatedAt = new Date().toISOString()
    return HttpResponse.json({ success: true })
  }),

  http.post(url('/login/webauthn'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    mockState.signedIn = true
    return HttpResponse.json({ redirectUrl: '/', authenticated: true })
  }),

  http.get(url('/v1/me'), () => {
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    return HttpResponse.json({
      user: mockState.user,
      workspaces: mockState.workspaces
        .filter((workspace) => !workspace.deletionScheduledFor || workspace.role === 'OWNER')
        .map((workspace) => ({
          ...workspace,
          // The family has a transaction once one is stored and not deleted.
          hasTransactions:
            workspace.hasTransactions ||
            mockState.transactions.some((tx) => !mockState.deletedTransactionIds.has(tx.id)),
        })),
      suggestedWorkspaceName: mockState.suggestedWorkspaceName,
    })
  }),

  http.get(url('/v1/me/activity'), () => {
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    return HttpResponse.json(mockState.activity)
  }),

  http.get(url('/v1/me/passkeys'), () => {
    if (!mockState.signedIn) return authRequired()
    return HttpResponse.json(mockState.passkeys)
  }),

  http.delete<{ id: string }>(url('/v1/me/passkeys/:id'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    const passkey = mockState.passkeys.find((candidate) => candidate.id === params.id)
    if (!passkey) return problem(404, 'passkey.not-found', 'Passkey não encontrada.')
    if (mockState.passkeys.length === 1 && !mockState.hasGoogle) {
      return rejected(
        'passkey.last-method',
        'Esta é a sua única forma de entrar. Cadastre outra passkey antes de remover esta.',
      )
    }
    mockState.passkeys = mockState.passkeys.filter((candidate) => candidate !== passkey)
    mockState.activity = [
      {
        id: crypto.randomUUID(),
        action: 'passkey.removed',
        occurredAt: new Date().toISOString(),
        workspaceId: null,
        actorName: mockState.user.displayName,
        byMe: true,
        metadata: {},
      },
      ...mockState.activity,
    ]
    return new HttpResponse(null, { status: 204 })
  }),

  http.post(url('/v1/me/export'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    return zipResponse('liberdade-meus-dados')
  }),

  http.post<{ ws: string }>(url('/v1/workspaces/:ws/export'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws), true)
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER') return roleDenied()
    return zipResponse('liberdade-familia')
  }),

  http.post(url('/v1/me/deletion'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    const scheduled = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString()
    mockState.user = {
      ...mockState.user,
      deletionScheduledFor: mockState.user.deletionScheduledFor ?? scheduled,
    }
    return HttpResponse.json(
      { deletionScheduledFor: mockState.user.deletionScheduledFor },
      { status: 202 },
    )
  }),

  http.delete(url('/v1/me/deletion'), ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    mockState.user = { ...mockState.user, deletionScheduledFor: null }
    return new HttpResponse(null, { status: 204 })
  }),

  http.post<{ ws: string }>(url('/v1/workspaces/:ws/deletion'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER') return roleDenied()
    const scheduled =
      workspace.deletionScheduledFor ??
      new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString()
    workspace.deletionScheduledFor = scheduled
    return HttpResponse.json({ deletionScheduledFor: scheduled }, { status: 202 })
  }),

  http.delete<{ ws: string }>(url('/v1/workspaces/:ws/deletion'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws), true)
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER') return roleDenied()
    workspace.deletionScheduledFor = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(url('/v1/me/documents'), () => {
    if (!mockState.signedIn) return authRequired()
    return HttpResponse.json(
      (['TERMS', 'PRIVACY'] as const).map((purpose) => {
        const accepted = mockState.acceptedDocuments[purpose]
        return {
          purpose,
          currentVersion: mockState.currentDocuments[purpose],
          acceptedVersion: accepted?.version ?? null,
          acceptedAt: accepted?.at ?? null,
        }
      }),
    )
  }),

  http.post(url('/v1/me/documents/accept'), async ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    const body = (await readJson(request)) as { terms?: unknown; privacy?: unknown } | undefined
    if (typeof body !== 'object' || body === null) return malformed()
    const errors: FieldError[] = []
    if (typeof body.terms !== 'string' || body.terms.trim() === '') {
      errors.push({ field: 'terms', code: 'invalid' })
    }
    if (typeof body.privacy !== 'string' || body.privacy.trim() === '') {
      errors.push({ field: 'privacy', code: 'invalid' })
    }
    if (errors.length > 0) return invalid(errors)
    if (
      body.terms !== mockState.currentDocuments.TERMS ||
      body.privacy !== mockState.currentDocuments.PRIVACY
    ) {
      return problem(
        409,
        'documents.version-outdated',
        'Os termos mudaram. Leia a versão atual e aceite de novo.',
      )
    }
    const now = new Date().toISOString()
    for (const purpose of ['TERMS', 'PRIVACY'] as const) {
      const accepted = mockState.acceptedDocuments[purpose]
      if (accepted?.version !== mockState.currentDocuments[purpose]) {
        mockState.acceptedDocuments[purpose] = {
          version: mockState.currentDocuments[purpose],
          at: now,
        }
      }
    }
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(url('/v1/me/consents'), () => {
    if (!mockState.signedIn) return authRequired()
    return HttpResponse.json(
      mockState.consents.map((consent) => {
        const view: Partial<typeof consent> = { ...consent }
        delete view.grantedByMe
        return view
      }),
    )
  }),

  http.delete(url('/v1/me/consents/:id'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    if (!mockState.signedIn) return authRequired()
    const consent = mockState.consents.find((candidate) => candidate.id === params.id)
    if (!consent) return problem(404, 'consent.not-found', 'Consentimento não encontrado.')
    if (!consent.grantedByMe) {
      return problem(403, 'consent.revoke-forbidden', 'Só quem deu o consentimento pode revogá-lo.')
    }
    // Withdrawn or superseded already: nothing left to do.
    if (consent.revokedAt !== null || consent.supersededAt !== null) {
      return new HttpResponse(null, { status: 204 })
    }
    const now = new Date().toISOString()
    consent.revokedAt = now
    consent.canRevoke = false
    // The youth the consent covers leaves the family, as the API does when the member is still a youth.
    const youth = (mockState.members[consent.workspaceId] ?? []).find(
      (member) =>
        member.nickname === consent.subjectName &&
        member.role === 'YOUTH' &&
        member.status === 'ACTIVE',
    )
    if (youth) youth.status = 'REMOVED'
    mockState.activity.unshift(
      {
        id: crypto.randomUUID(),
        action: 'member.removed',
        occurredAt: now,
        workspaceId: consent.workspaceId,
        actorName: mockState.user.displayName,
        byMe: true,
        metadata: { reason: 'CONSENT_REVOKED' },
      },
      {
        id: crypto.randomUUID(),
        action: 'consent.revoked',
        occurredAt: now,
        workspaceId: consent.workspaceId,
        actorName: mockState.user.displayName,
        byMe: true,
        metadata: { purpose: 'PARENTAL' },
      },
    )
    return new HttpResponse(null, { status: 204 })
  }),

  http.patch(url('/v1/workspaces/:ws'), async ({ request, params }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    const workspace = mockState.workspaces.find((candidate) => candidate.id === params.ws)
    if (!workspace) return problem(404, 'workspace.not-found', 'Família não encontrada.')
    const body = (await request.json()) as { name?: string }
    const name = (body.name ?? '').trim()
    if (name === '') {
      return problem(422, 'request.invalid', 'Revise os campos.', [
        { field: 'name', code: 'NotBlank' },
      ])
    }
    workspace.name = name
    return HttpResponse.json(workspace)
  }),

  http.get(url('/v1/workspaces/:ws/members'), ({ params }) => {
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    const members = mockState.members[String(params.ws)]
    if (!members) return problem(404, 'workspace.not-found', 'Família não encontrada.')
    const active = (member: Schemas['MemberView']) => member.status === 'ACTIVE'
    return HttpResponse.json([...members].sort((a, b) => Number(!active(a)) - Number(!active(b))))
  }),

  http.patch(url('/v1/workspaces/:ws/members/:id'), async ({ request, params }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    const members = mockState.members[String(params.ws)] ?? []
    const member = members.find((candidate) => candidate.id === params.id)
    if (!member) return problem(404, 'member.not-found', 'Pessoa não encontrada.')
    const body = (await request.json()) as {
      role?: Schemas['MemberView']['role'] | null
      nickname?: string | null
      parentalConsent?: boolean | null
    }
    if (body.role == null && body.nickname === undefined) {
      return problem(422, 'request.invalid', 'Revise os campos.')
    }
    if (member.status === 'REMOVED') {
      return problem(422, 'member.removed', 'Essa pessoa já saiu da família.')
    }
    if (body.role != null && body.role !== member.role) {
      const owners = members.filter((m) => m.role === 'OWNER' && m.status === 'ACTIVE')
      if (member.role === 'OWNER' && owners.length <= 1) return lastOwner()
      if (body.role === 'YOUTH' && member.you) {
        return problem(
          422,
          'member.self-youth',
          'Você não pode mudar o seu próprio papel para Jovem.',
        )
      }
      if (body.role === 'YOUTH' && body.parentalConsent !== true) {
        return problem(
          422,
          'member.parental-consent-required',
          'Para ser Jovem, é preciso o consentimento do responsável.',
        )
      }
      member.role = body.role
      const workspace = mockState.workspaces.find((candidate) => candidate.id === params.ws)
      if (workspace && member.you) workspace.role = body.role
    }
    if (body.nickname !== undefined) member.nickname = body.nickname?.trim() || null
    return HttpResponse.json(member)
  }),

  http.delete(url('/v1/workspaces/:ws/members/:id'), ({ request, params }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    const members = mockState.members[String(params.ws)] ?? []
    const member = members.find((candidate) => candidate.id === params.id)
    if (!member) return problem(404, 'member.not-found', 'Pessoa não encontrada.')
    if (member.status === 'ACTIVE') {
      const owners = members.filter((m) => m.role === 'OWNER' && m.status === 'ACTIVE')
      if (member.role === 'OWNER' && owners.length <= 1) return lastOwner()
      member.status = 'REMOVED'
    }
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(url('/v1/workspaces/:ws/invitations'), ({ params }) => {
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER' && workspace.role !== 'ADULT') return roleDenied()
    return HttpResponse.json(
      mockState.invitations
        .filter(
          (invitation) =>
            invitation.workspaceId === workspace.id &&
            invitation.view.kind === 'INVITE' &&
            !invitationGone(invitation),
        )
        .map((invitation) => ({ ...invitation.view, token: null })),
    )
  }),

  http.post(url('/v1/workspaces/:ws/invitations'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER' && workspace.role !== 'ADULT') return roleDenied()
    const body = (await readJson(request)) as
      { role?: unknown; nickname?: unknown; parentalConsent?: unknown } | undefined
    if (typeof body !== 'object' || body === null) return malformed()
    const nickname = typeof body.nickname === 'string' ? body.nickname.trim() : ''
    const errors: FieldError[] = []
    if (!INVITE_ROLES.includes(body.role)) errors.push({ field: 'role', code: 'invalid' })
    if (nickname === '' || nickname.length > MAX_NAME_LENGTH) {
      errors.push({ field: 'nickname', code: 'invalid' })
    }
    if (errors.length > 0) return invalid(errors)
    const role = body.role as Schemas['InvitationView']['role']
    if (role === 'OWNER' && workspace.role !== 'OWNER') {
      return problem(
        403,
        'invitation.role-forbidden',
        'Só quem é Dona ou Dono pode convidar outra Dona ou Dono.',
      )
    }
    if (role === 'YOUTH' && body.parentalConsent !== true) {
      return rejected(
        'invitation.parental-consent-required',
        'Para convidar um Jovem, é preciso o consentimento do responsável.',
      )
    }
    const creator = mockState.members[workspace.id]?.find(
      (member) => member.id === workspace.membershipId,
    )
    const token = newToken()
    const view: Schemas['InvitationView'] = {
      id: crypto.randomUUID(),
      role,
      nickname,
      kind: 'INVITE',
      expiresAt: new Date(Date.now() + 7 * DAY).toISOString(),
      createdByName: creator?.nickname ?? creator?.displayName ?? null,
      token: null,
    }
    mockState.invitations.push({ view, token, status: 'PENDING', workspaceId: workspace.id })
    workspace.familyInvited = true
    return HttpResponse.json({ ...view, token }, { status: 201 })
  }),

  http.post(url('/v1/workspaces/:ws/members/:id/recovery'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER' && workspace.role !== 'ADULT') return roleDenied()
    const members = mockState.members[workspace.id] ?? []
    const target = members.find((candidate) => candidate.id === params.id)
    if (!target) return problem(404, 'member.not-found', 'Pessoa não encontrada.')
    if (target.role === 'OWNER' && workspace.role !== 'OWNER') {
      return problem(
        403,
        'invitation.role-forbidden',
        'Só quem é Dona ou Dono pode trazer outra Dona ou Dono de volta.',
      )
    }
    if (target.status !== 'ACTIVE') {
      return rejected('member.removed', 'Esta pessoa já saiu da família.')
    }
    if (!target.passkeyOnly) {
      return rejected(
        'member.recovery-unavailable',
        'Esta pessoa entra com o Google; é por lá que ela recupera o acesso.',
      )
    }
    // One pending link per person: a new one replaces the old.
    for (const previous of mockState.invitations) {
      if (
        previous.status === 'PENDING' &&
        previous.view.kind === 'RECOVERY' &&
        previous.targetMembershipId === target.id
      ) {
        previous.status = 'REVOKED'
      }
    }
    const creator = members.find((member) => member.id === workspace.membershipId)
    const token = newToken()
    const view: Schemas['InvitationView'] = {
      id: crypto.randomUUID(),
      role: target.role,
      nickname: null,
      kind: 'RECOVERY',
      expiresAt: new Date(Date.now() + DAY).toISOString(),
      createdByName: creator?.nickname ?? creator?.displayName ?? null,
      token: null,
    }
    mockState.invitations.push({
      view,
      token,
      status: 'PENDING',
      workspaceId: workspace.id,
      targetMembershipId: target.id,
    })
    return HttpResponse.json({ ...view, token }, { status: 201 })
  }),

  http.delete(url('/v1/workspaces/:ws/invitations/:id'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER' && workspace.role !== 'ADULT') return roleDenied()
    const invitation = mockState.invitations.find(
      (candidate) => candidate.workspaceId === workspace.id && candidate.view.id === params.id,
    )
    if (!invitation) return invitationNotFound()
    // A link already used or cancelled stays as it is.
    if (invitation.status === 'PENDING') invitation.status = 'REVOKED'
    return new HttpResponse(null, { status: 204 })
  }),

  http.get(url('/v1/workspaces/:ws/modules'), ({ params }) => {
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    return HttpResponse.json(moduleViews(workspace.id))
  }),

  http.put(url('/v1/workspaces/:ws/modules/:key'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER' && workspace.role !== 'ADULT') return roleDenied()
    const key = MODULE_KEYS.find((candidate) => candidate === params.key)
    if (!key) return problem(404, 'module.not-found', 'Módulo não encontrado.')
    const body = (await readJson(request)) as { enabled?: unknown } | undefined
    if (typeof body !== 'object' || body === null) return malformed()
    if (typeof body.enabled !== 'boolean') return invalid([{ field: 'enabled', code: 'NotNull' }])
    mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], [key]: body.enabled }
    return HttpResponse.json({ key, enabled: body.enabled })
  }),

  http.post(url('/v1/invitations/preview'), async ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    const body = (await readJson(request)) as { token?: unknown } | undefined
    if (typeof body !== 'object' || body === null || typeof body.token !== 'string') {
      return malformed()
    }
    const invitation = mockState.invitations.find((candidate) => candidate.token === body.token)
    if (!invitation) return invitationNotFound()
    if (invitationGone(invitation)) return invitationGoneProblem()
    const recoveryTarget = recoveryTargetOf(invitation)
    if (invitation.view.kind === 'RECOVERY' && recoveryTarget?.status !== 'ACTIVE') {
      return invitationGoneProblem()
    }
    const workspace = mockState.workspaces.find(
      (candidate) => candidate.id === invitation.workspaceId,
    )
    return HttpResponse.json({
      workspaceName: workspace?.name ?? invitation.workspaceName ?? '',
      invitedBy: invitation.view.createdByName,
      role: invitation.view.role,
      nickname: recoveryTarget ? recoveryTarget.nickname : invitation.view.nickname,
      kind: invitation.view.kind,
    })
  }),

  http.post(url('/v1/invitations/accept'), async ({ request }) => {
    if (withoutToken(request)) return csrfInvalid()
    const body = (await readJson(request)) as
      { token?: unknown; newUser?: { displayName?: unknown } } | undefined
    if (typeof body !== 'object' || body === null || typeof body.token !== 'string') {
      return malformed()
    }
    const invitation = mockState.invitations.find((candidate) => candidate.token === body.token)
    if (!invitation) return invitationNotFound()
    if (invitationGone(invitation)) return invitationGoneProblem()
    const workspaceId = invitation.workspaceId
    const members = (mockState.members[workspaceId] ??= [])
    if (invitation.view.kind === 'RECOVERY') {
      // The target signs in whoever holds the link, and `newUser` plays no part.
      const target = recoveryTargetOf(invitation)
      if (target?.status !== 'ACTIVE') return invitationGoneProblem()
      invitation.status = 'USED'
      mockState.signedIn = true
      mockState.user = {
        ...mockState.user,
        displayName: target.displayName ?? mockState.user.displayName,
      }
      joinWorkspace(invitation, target.id, target.role)
      return HttpResponse.json({ workspaceId, membershipId: target.id, mustRegisterPasskey: true })
    }
    const signedIn = mockState.signedIn
    let displayName = mockState.user.displayName
    if (!signedIn) {
      const named = body.newUser?.displayName
      if (typeof named !== 'string' || named.trim() === '') {
        return rejected(
          'invitation.new-user-required',
          'Diga como você se chama para entrar na família.',
        )
      }
      displayName = named.trim()
    } else if (members.some((member) => member.you && member.status === 'ACTIVE')) {
      return problem(409, 'invitation.already-member', 'Você já faz parte desta família.')
    }
    const membershipId = crypto.randomUUID()
    members.push({
      id: membershipId,
      displayName,
      nickname: invitation.view.nickname,
      role: invitation.view.role,
      status: 'ACTIVE',
      you: true,
      passkeyOnly: true,
      joinedAt: new Date().toISOString(),
    })
    invitation.status = 'USED'
    if (!signedIn) {
      mockState.signedIn = true
      mockState.user = { ...mockState.user, displayName }
    }
    joinWorkspace(invitation, membershipId, invitation.view.role)
    return HttpResponse.json({ workspaceId, membershipId, mustRegisterPasskey: !signedIn })
  }),

  http.post(url('/v1/me/getting-started/dismiss'), ({ request }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    const id = new URL(request.url).searchParams.get('workspaceId')
    const workspace = mockState.workspaces.find((candidate) => candidate.id === id)
    if (!workspace) return problem(404, 'workspace.not-found', 'Família não encontrada.')
    // A repeat keeps the first date.
    workspace.gettingStartedDismissedAt ??= new Date().toISOString()
    return new HttpResponse(null, { status: 204 })
  }),

  http.post(url('/v1/workspaces'), async ({ request }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
    const body = (await readJson(request)) as { name?: unknown; timezone?: unknown } | undefined
    if (typeof body !== 'object' || body === null) return malformed()
    const name = typeof body.name === 'string' ? body.name.trim() : ''
    if (name === '') return invalid([{ field: 'name', code: 'NotBlank' }])
    if (name.length > MAX_NAME_LENGTH) return invalid([{ field: 'name', code: 'Size' }])
    if (mockState.workspaces.length > 0) {
      return problem(409, 'workspace.already-exists', 'Você já faz parte de uma família.')
    }
    const timezone = isKnownTimezone(body.timezone) ? body.timezone : DEFAULT_TIMEZONE
    const { id, role } = createFamily(name, timezone)
    return HttpResponse.json({ id, name, role }, { status: 201 })
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/accounts'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    return HttpResponse.json([...mockState.accounts].sort(activeFirst).map(accountView))
  }),

  http.post<{ ws: string }>(url('/v1/workspaces/:ws/accounts'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const body = (await readJson(request)) as Record<string, unknown> | undefined
    if (typeof body !== 'object' || body === null) return malformed()
    const initial = readMoney(body.initialBalance)
    const owner = body.ownerMembershipId
    if (
      initial === undefined ||
      (body.type != null && !ACCOUNT_TYPES.includes(body.type)) ||
      (body.name != null && typeof body.name !== 'string') ||
      (owner != null && (typeof owner !== 'string' || !UUID.test(owner)))
    ) {
      return malformed()
    }
    const name = typeof body.name === 'string' ? body.name.trim() : ''
    const errors: FieldError[] = []
    if (name === '') errors.push({ field: 'name', code: 'NotBlank' })
    else if (!validName(name)) errors.push({ field: 'name', code: 'Size' })
    if (body.type == null) errors.push({ field: 'type', code: 'NotNull' })
    if (initial === null) errors.push({ field: 'initialBalance', code: 'NotNull' })
    else if (!withinBalanceLimit(initial.cents)) {
      errors.push({ field: 'initialBalance', code: 'BalanceAmount' })
    }
    if (errors.length > 0 || initial === null) return invalid(errors)
    if (initial.currency !== workspace.currency) return currencyUnsupported()
    if (typeof owner === 'string' && owner !== workspace.membershipId) {
      return rejected('account.owner-not-found', 'Essa pessoa não faz parte da família.')
    }
    if (nameInUse(name)) return nameTaken()
    const account: MockAccount = {
      id: crypto.randomUUID(),
      name,
      type: body.type as MockAccount['type'],
      currency: workspace.currency,
      initialBalance: money(initial.cents, workspace.currency),
      ownerMembershipId: typeof owner === 'string' ? owner : null,
      archivedAt: null,
    }
    mockState.accounts.push(account)
    return HttpResponse.json(accountView(account), { status: 201 })
  }),

  http.patch<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/accounts/:id'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as Record<string, unknown> | undefined
      if (typeof body !== 'object' || body === null) return malformed()
      const initial = readMoney(body.initialBalance)
      const balance = readMoney(body.balance)
      if (
        initial === undefined ||
        balance === undefined ||
        (body.type != null && !ACCOUNT_TYPES.includes(body.type)) ||
        (body.name != null && typeof body.name !== 'string')
      ) {
        return malformed()
      }
      const name = typeof body.name === 'string' ? body.name.trim() : null
      const errors: FieldError[] = []
      if (name !== null && !validName(name)) errors.push({ field: 'name', code: 'Size' })
      if (initial !== null && !withinBalanceLimit(initial.cents)) {
        errors.push({ field: 'initialBalance', code: 'BalanceAmount' })
      }
      if (balance !== null && !withinBalanceLimit(balance.cents)) {
        errors.push({ field: 'balance', code: 'BalanceAmount' })
      }
      if (initial !== null && balance !== null) {
        errors.push({ field: 'oneBalanceAtMost', code: 'AssertTrue' })
      }
      if (name === null && body.type == null && initial === null && balance === null) {
        errors.push({ field: 'notEmpty', code: 'AssertTrue' })
      }
      if (errors.length > 0) return invalid(errors)
      const account = mockState.accounts.find((existing) => existing.id === params.id)
      if (!account) return accountNotFound()
      if (
        [initial, balance].some((value) => value !== null && value.currency !== account.currency)
      ) {
        return currencyUnsupported()
      }
      // Only active accounts hold a name: renaming an archived one never clashes.
      if (name !== null && account.archivedAt === null && nameInUse(name, account.id)) {
        return nameTaken()
      }
      if (name !== null) account.name = name
      if (body.type != null) account.type = body.type as MockAccount['type']
      if (initial !== null) account.initialBalance = money(initial.cents, account.currency)
      if (balance !== null) {
        account.initialBalance = money(balance.cents - movementCents(account.id), account.currency)
      }
      return HttpResponse.json(accountView(account))
    },
  ),

  http.put<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/accounts/:id/owner'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as Record<string, unknown> | undefined
      if (typeof body !== 'object' || body === null) return malformed()
      const owner = body.membershipId
      if (owner != null && (typeof owner !== 'string' || !UUID.test(owner))) return malformed()
      const account = mockState.accounts.find((existing) => existing.id === params.id)
      if (!account) return accountNotFound()
      if (typeof owner === 'string' && !activeMember(workspace, owner)) {
        return rejected('account.owner-not-found', 'Essa pessoa não faz parte da família.')
      }
      account.ownerMembershipId = typeof owner === 'string' ? owner : null
      return HttpResponse.json(accountView(account))
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/accounts/:id/archive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const account = mockState.accounts.find((existing) => existing.id === params.id)
      if (!account) return accountNotFound()
      if (account.archivedAt === null) {
        if (mockState.accounts.filter((existing) => existing.archivedAt === null).length <= 1) {
          return rejected('account.last-active', 'A família precisa de pelo menos uma conta ativa.')
        }
        account.archivedAt = new Date().toISOString()
      }
      return HttpResponse.json(accountView(account))
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/accounts/:id/unarchive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const account = mockState.accounts.find((existing) => existing.id === params.id)
      if (!account) return accountNotFound()
      if (account.archivedAt !== null) {
        if (nameInUse(account.name)) return nameTaken()
        account.archivedAt = null
      }
      return HttpResponse.json(accountView(account))
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/categories'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const kind = new URL(request.url).searchParams.get('kind')
    if (kind !== 'EXPENSE' && kind !== 'INCOME') return malformed()
    // Active ones first, most used in the last 90 days first; ties keep the seed order (the sort is
    // stable). Then the archived ones, by name and id.
    const since = new Date(Date.now() - 89 * DAY).toISOString().slice(0, 10)
    const uses = (categoryId: string) =>
      activeTransactions().filter(
        (transaction) => transaction.categoryId === categoryId && transaction.occurredOn >= since,
      ).length
    const ofKind = mockState.categories.filter((category) => category.kind === kind)
    const active = ofKind
      .filter((category) => category.archivedAt === null)
      .sort((a, b) => uses(b.id) - uses(a.id))
    const archived = ofKind
      .filter((category) => category.archivedAt !== null)
      .sort((a, b) => a.name.localeCompare(b.name) || a.id.localeCompare(b.id))
    return HttpResponse.json([...active, ...archived])
  }),

  http.post<{ ws: string }>(url('/v1/workspaces/:ws/categories'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const body = (await readJson(request)) as Record<string, unknown> | undefined
    if (typeof body !== 'object' || body === null) return malformed()
    const { icon, color, parentId } = body
    if (
      (body.name != null && typeof body.name !== 'string') ||
      (body.kind != null && !KINDS.includes(body.kind)) ||
      (icon != null && typeof icon !== 'string') ||
      (color != null && !CATEGORY_COLORS.includes(color)) ||
      (parentId != null && (typeof parentId !== 'string' || !UUID.test(parentId)))
    ) {
      return malformed()
    }
    const name = typeof body.name === 'string' ? body.name.trim() : ''
    const errors: FieldError[] = []
    if (name === '') errors.push({ field: 'name', code: 'NotBlank' })
    else if (name.length > MAX_CATEGORY_NAME_LENGTH) errors.push({ field: 'name', code: 'Size' })
    if (body.kind == null) errors.push({ field: 'kind', code: 'NotNull' })
    if (typeof icon === 'string' && (icon === '' || icon.length > MAX_ICON_LENGTH)) {
      errors.push({ field: 'icon', code: 'Size' })
    }
    if (errors.length > 0) return invalid(errors)
    if (body.kind === 'TRANSFER') {
      return rejected('category.kind-invalid', 'Categorias são de receita ou de gasto.')
    }
    const parent = typeof parentId === 'string' ? parentId : null
    if (parent !== null) {
      const found = mockState.categories.find((existing) => existing.id === parent)
      if (
        !found ||
        found.archivedAt !== null ||
        found.parentId !== null ||
        found.kind !== body.kind
      ) {
        return rejected(
          'category.parent-invalid',
          'Escolha uma categoria principal ativa, do mesmo tipo.',
        )
      }
    }
    if (categoryNameInUse(name, body.kind as CategoryKindName, parent)) return categoryNameTaken()
    const category: Schemas['CategoryView'] = {
      id: crypto.randomUUID(),
      name,
      kind: body.kind as CategoryKindName,
      icon: typeof icon === 'string' ? icon : null,
      color: typeof color === 'string' ? (color as CategoryColor) : null,
      parentId: parent,
      archivedAt: null,
    }
    mockState.categories.push(category)
    return HttpResponse.json(category, { status: 201 })
  }),

  http.patch<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/categories/:id'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as Record<string, unknown> | undefined
      if (typeof body !== 'object' || body === null) return malformed()
      const { icon, color } = body
      if (
        (body.name != null && typeof body.name !== 'string') ||
        (icon != null && typeof icon !== 'string') ||
        (color != null && !CATEGORY_COLORS.includes(color))
      ) {
        return malformed()
      }
      const name = typeof body.name === 'string' ? body.name.trim() : null
      const errors: FieldError[] = []
      if (name !== null && (name === '' || name.length > MAX_CATEGORY_NAME_LENGTH)) {
        errors.push({ field: 'name', code: 'Size' })
      }
      if (typeof icon === 'string' && (icon === '' || icon.length > MAX_ICON_LENGTH)) {
        errors.push({ field: 'icon', code: 'Size' })
      }
      if (name === null && icon == null && color == null) {
        errors.push({ field: 'notEmpty', code: 'AssertTrue' })
      }
      if (errors.length > 0) return invalid(errors)
      const category = mockState.categories.find((existing) => existing.id === params.id)
      if (!category) return categoryNotFound()
      // Only active categories hold a name: renaming an archived one never clashes.
      if (
        name !== null &&
        category.archivedAt === null &&
        categoryNameInUse(name, category.kind, category.parentId, category.id)
      ) {
        return categoryNameTaken()
      }
      if (name !== null) category.name = name
      if (typeof icon === 'string') category.icon = icon
      if (typeof color === 'string') category.color = color as CategoryColor
      return HttpResponse.json(category)
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/categories/:id/archive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const category = mockState.categories.find((existing) => existing.id === params.id)
      if (!category) return categoryNotFound()
      if (category.archivedAt === null) {
        const activeTopLevel = mockState.categories.filter(
          (existing) =>
            existing.kind === category.kind &&
            existing.parentId === null &&
            existing.archivedAt === null,
        )
        if (category.parentId === null && activeTopLevel.length <= 1) {
          return rejected(
            'category.last-active',
            'É preciso manter pelo menos uma categoria ativa desse tipo.',
          )
        }
        const now = new Date().toISOString()
        for (const each of mockState.categories) {
          if (
            (each.id === category.id || each.parentId === category.id) &&
            each.archivedAt === null
          ) {
            each.archivedAt = now
          }
        }
      }
      return HttpResponse.json(category)
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/categories/:id/unarchive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const category = mockState.categories.find((existing) => existing.id === params.id)
      if (!category) return categoryNotFound()
      if (category.archivedAt !== null) {
        const parent = mockState.categories.find((existing) => existing.id === category.parentId)
        if (parent && parent.archivedAt !== null) {
          return rejected('category.parent-archived', 'Reative a categoria principal primeiro.')
        }
        const group = mockState.categories.filter(
          (each) =>
            each.id === category.id ||
            (each.parentId === category.id && each.archivedAt === category.archivedAt),
        )
        if (group.some((each) => categoryNameInUse(each.name, each.kind, each.parentId, each.id))) {
          return categoryNameTaken()
        }
        for (const each of group) each.archivedAt = null
      }
      return HttpResponse.json(category)
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/transactions'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const search = new URL(request.url).searchParams
    const month = search.get('month') ?? ''
    const categoryId = search.get('categoryId')
    const accountId = search.get('accountId')
    if (!MONTH.test(month)) return malformed()
    if ([categoryId, accountId].some((id) => id !== null && !UUID.test(id))) return malformed()
    const outOfRange = monthOutOfRange(workspace, month)
    if (outOfRange) return outOfRange
    // A category takes its subcategories along; an account, what leaves it and what enters it.
    const inCategory = new Set(
      mockState.categories
        .filter((category) => category.id === categoryId || category.parentId === categoryId)
        .map((category) => category.id),
    )
    return HttpResponse.json(
      activeTransactions()
        .filter(
          (transaction) =>
            transaction.occurredOn.startsWith(`${month}-`) &&
            (categoryId === null ||
              (transaction.categoryId !== null && inCategory.has(transaction.categoryId))) &&
            (accountId === null ||
              transaction.accountId === accountId ||
              transaction.toAccountId === accountId),
        )
        .sort(newestFirst),
    )
  }),

  http.post<{ ws: string }>(url('/v1/workspaces/:ws/transactions'), async ({ request, params }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    // The API binds the Idempotency-Key as a UUID, so a missing or unreadable key is a 400.
    const key = request.headers.get('Idempotency-Key')
    const body = (await readJson(request)) as CreateTransactionRequest | undefined
    if (key === null || !UUID.test(key)) return malformed()
    if (typeof body !== 'object' || body === null || !readable(body)) return malformed()
    const { errors, cents, description } = validateTransaction(body, CREATE_REQUIRED)
    if (errors.length > 0) return invalid(errors)
    if (body.id !== key || !UUID_V7.test(body.id)) {
      return rejected('transaction.invalid-id', 'O identificador do lançamento é inválido.')
    }
    const transaction: TransactionView = {
      id: body.id,
      kind: body.kind,
      amount: money(cents, body.amount.currency),
      occurredOn: body.occurredOn,
      accountId: body.accountId,
      toAccountId: body.toAccountId ?? null,
      categoryId: body.categoryId ?? null,
      description,
      memberId: body.memberId ?? workspace.membershipId,
      createdAt: new Date().toISOString(),
      version: 0,
    }
    // A deleted transaction keeps its id: a resend of it still answers the stored one.
    const stored = mockState.transactions.find((existing) => existing.id === body.id)
    if (stored) {
      return sameContent(stored, transaction)
        ? HttpResponse.json(stored, { status: 200 })
        : problem(409, 'transaction.id-conflict', 'Este lançamento já existe com outros dados.')
    }
    const failure = familyRuleFailure(workspace, transaction)
    if (failure) return failure
    mockState.transactions.push(transaction)
    return HttpResponse.json(transaction, { status: 201 })
  }),

  http.patch<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/transactions/:id'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as UpdateTransactionRequest | undefined
      if (typeof body !== 'object' || body === null || !readable(body)) return malformed()
      const { errors, cents, description } = validateTransaction(body, UPDATE_REQUIRED)
      if (errors.length > 0) return invalid(errors)
      const stored = storedTransaction(params.id)
      if (!stored) return transactionNotFound()
      const { transaction: current, deleted } = stored
      if (deleted) return problem(409, 'transaction.deleted', 'Este lançamento foi excluído.')
      // A stale version wins over an invalid payload: the person first needs to see what changed.
      if (current.version !== body.version) {
        return problem(
          409,
          'transaction.version-conflict',
          'Este lançamento mudou desde que você abriu.',
          undefined,
          { current },
        )
      }
      const edited: TransactionView = {
        ...current,
        kind: body.kind,
        amount: money(cents, body.amount.currency),
        occurredOn: body.occurredOn,
        accountId: body.accountId,
        toAccountId: body.toAccountId ?? null,
        categoryId: body.categoryId ?? null,
        description,
        memberId: body.memberId ?? current.memberId,
        version: current.version + 1,
      }
      const failure = familyRuleFailure(workspace, edited, current)
      if (failure) return failure
      Object.assign(current, edited)
      return HttpResponse.json(current)
    },
  ),

  http.get<{ ws: string; id: string }>(url('/v1/workspaces/:ws/transactions/:id'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!UUID.test(params.id)) return malformed()
    const stored = storedTransaction(params.id)
    if (!stored) return transactionNotFound()
    if (stored.deleted) return problem(409, 'transaction.deleted', 'Este lançamento foi excluído.')
    return HttpResponse.json(stored.transaction)
  }),

  http.delete<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/transactions/:id'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const stored = storedTransaction(params.id)
      if (!stored) return transactionNotFound()
      // Deleting it again changes nothing.
      if (!stored.deleted) {
        mockState.deletedTransactionIds.add(params.id)
        stored.transaction.version += 1
      }
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/transactions/:id/restore'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const stored = storedTransaction(params.id)
      if (!stored) return transactionNotFound()
      // An active one comes back unchanged, so a repeated "Desfazer" is harmless.
      if (stored.deleted) {
        mockState.deletedTransactionIds.delete(params.id)
        stored.transaction.version += 1
      }
      return HttpResponse.json(stored.transaction)
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/dashboard'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const month = new URL(request.url).searchParams.get('month') ?? ''
    if (!MONTH.test(month)) return malformed()
    const outOfRange = monthOutOfRange(workspace, month)
    if (outOfRange) return outOfRange
    return HttpResponse.json(dashboardFor(month, workspace.currency))
  }),
]
