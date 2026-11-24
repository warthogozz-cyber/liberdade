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
import {
  createFamily,
  keepsAcceptance,
  type MockAccount,
  type MockBudget,
  type MockGoal,
  type MockInvitation,
  type MockRule,
  mockState,
} from './data'

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
const SAO_PAULO_OFFSET_MS = 3 * 60 * 60 * 1000

/**
 * The refund deadline as RefundWindow.java computes it: 00:00 in São Paulo (UTC-3, no daylight saving)
 * eight local days after the payment day, so the last allowed day is the seventh one.
 */
function refundDeadline(paidAt: number): string {
  const local = new Date(paidAt - SAO_PAULO_OFFSET_MS)
  return new Date(
    Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + 8) +
      SAO_PAULO_OFFSET_MS,
  ).toISOString()
}

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
    country: 'BR',
    locale: 'pt-BR',
    paymentMethods: ['PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BOLETO', 'BANK_TRANSFER', 'CASH'],
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

/** The paid plan running, or billing off: the API then opens the import to every family. */
function importAllowed(): boolean {
  return mockState.billing.plan.key === 'FAMILY' || !mockState.billing.selling
}

/** The description key the category hints match: the one line the mock knows. */
const HINTED_KEY = 'padaria pao quente'

/** The transactions not deleted, planned ones included: what the list shows. */
function listedTransactions(): TransactionView[] {
  return mockState.transactions.filter(
    (transaction) => !mockState.deletedTransactionIds.has(transaction.id),
  )
}

/** The transactions not deleted and confirmed: the only ones sums see, so a planned one counts nowhere. */
function activeTransactions(): TransactionView[] {
  return listedTransactions().filter((transaction) => transaction.status === 'CONFIRMED')
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
    stored.description === request.description &&
    stored.paymentMethod === request.paymentMethod &&
    sameNames(stored.tags, request.tags)
  )
}

const tagKey = (name: string) => name.trim().toLowerCase()

function sameNames(a: string[], b: string[]) {
  const keys = (names: string[]) => [...new Set(names.map(tagKey))].sort()
  return JSON.stringify(keys(a)) === JSON.stringify(keys(b))
}

/** The names a transaction ends up with: each tag once, spelled as the family's tag if it has one. */
function tagNamesOf(names: (string | null)[]): string[] {
  const byKey = new Map<string, string>()
  for (const raw of names) {
    const name = (raw ?? '').trim()
    if (!byKey.has(tagKey(name))) {
      const known = mockState.tags.find(
        (tag) => tag.archivedAt === null && tagKey(tag.name) === tagKey(name),
      )
      byKey.set(tagKey(name), known ? known.name : name)
    }
  }
  return [...byKey.values()].sort((x, y) => tagKey(x).localeCompare(tagKey(y)))
}

/** Makes the tags of these names that the family does not have yet. */
function makeTags(names: string[]) {
  for (const name of names) {
    const known = mockState.tags.some(
      (tag) => tag.archivedAt === null && tagKey(tag.name) === tagKey(name),
    )
    if (!known) mockState.tags.push({ id: crypto.randomUUID(), name, archivedAt: null })
  }
}

const tagNotFound = () => problem(404, 'tag.not-found', 'Tag não encontrada.')
const MAX_TAGS = 200
const MAX_TAG_NAMES = 5
const MAX_TAG_LENGTH = 30

/** The field errors of a list of tag names, as the API's validator finds them. */
function tagErrors(names: unknown): FieldError[] {
  if (names === undefined || names === null) return []
  const valid =
    Array.isArray(names) &&
    names.every(
      (name) =>
        typeof name === 'string' &&
        name.trim().length > 0 &&
        [...name.trim()].length <= MAX_TAG_LENGTH &&
        // eslint-disable-next-line no-control-regex
        !/[\u0000-\u001f\u007f-\u009f]/.test(name),
    ) &&
    new Set(names.map((name: string) => tagKey(name))).size <= MAX_TAG_NAMES
  return valid ? [] : [{ field: 'tags', code: 'TagNames' }]
}

/** Whether the names would make the family's 201st active tag. */
function overTagLimit(names: string[]) {
  const fresh = names.filter(
    (name) =>
      !mockState.tags.some((tag) => tag.archivedAt === null && tagKey(tag.name) === tagKey(name)),
  )
  return mockState.tags.filter((tag) => tag.archivedAt === null).length + fresh.length > MAX_TAGS
}

const tagLimit = () =>
  rejected('tag.limit', `A família já tem ${MAX_TAGS} tags. Arquive alguma para criar outra.`)

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
function categoriesFor(month: string, currency: string, paymentMethod: string | null) {
  const [year, number] = month.split('-').map(Number)
  const previousMonth = new Date(Date.UTC(year, number - 2, 1)).toISOString().slice(0, 7)
  const totals = new Map<string, { current: bigint; previous: bigint }>()
  for (const transaction of activeTransactions()) {
    if (transaction.kind !== 'EXPENSE' || transaction.categoryId === null) continue
    if (paymentMethod !== null && transaction.paymentMethod !== paymentMethod) continue
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
function dashboardFor(month: string, currency: string, paymentMethod: string | null = null) {
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
    // The payment method narrows what was spent and earned; the balance stays the accounts' own.
    if (paymentMethod !== null && transaction.paymentMethod !== paymentMethod) continue
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
    byCategory: categoriesFor(month, currency, paymentMethod),
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
  paymentMethod: string | null
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
  if (transfer && proposed.paymentMethod !== null) {
    return rejected(
      'transaction.payment-method-transfer',
      'Uma transferência não leva forma de pagamento.',
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

type RuleBody = Partial<Schemas['CreateRecurringRuleRequest']>

const FREQUENCIES: unknown[] = ['WEEKLY', 'MONTHLY', 'YEARLY']
const ruleNotFound = () => problem(404, 'recurring-rule.not-found', 'Recorrência não encontrada.')
const planningOff = () =>
  problem(403, 'module.disabled', 'O módulo de planejamento está desligado.')
const planningOn = (workspace: MeWorkspace) => mockState.modules[workspace.id]?.planning === true
/** Only an Owner or an Adult makes, edits and archives rules. */
const canManageRules = (workspace: MeWorkspace) =>
  workspace.role === 'OWNER' || workspace.role === 'ADULT'

const budgetInvalid = () => problem(400, 'request.invalid', 'Alguns campos precisam de ajuste.')
const budgetConflict = () =>
  problem(
    409,
    'budget.version-conflict',
    'O limite foi alterado por outra pessoa. Recarregue e tente de novo.',
  )

const GOAL_ICONS: unknown[] = [
  'savings',
  'home',
  'car',
  'travel',
  'education',
  'health',
  'gift',
  'party',
  'tech',
  'other',
]
const FREE_ACTIVE_GOALS = 3
const MAX_GOAL_NAME_LENGTH = 40
const goalNotFound = () => problem(404, 'goal.not-found', 'Meta não encontrada.')
const goalRejected = (code: string, detail: string) => problem(422, code, detail)
const goalLimitReached = () =>
  problem(
    403,
    'goal.limit-reached',
    'No plano Grátis, a família tem até 3 metas ativas. Conclua ou arquive uma, ou veja o plano Família em Mais > Plano.',
  )
const thisMonth = () => new Date().toISOString().slice(0, 7)

/** The reserve as the mock knows it: no reserve accounts, so the balance is zero; the typed cost wins over the history's. */
function reserveView(): Schemas['ReserveView'] {
  const { targetMonths, manualMonthlyCost } = mockState.reserve
  const history = mockState.spendingHistoryCents
  const average = manualMonthlyCost ?? (history === null ? null : money(history, 'BRL'))
  const cents = average ? parseCents(average.amount) : null
  return {
    balance: money(0n, 'BRL'),
    hasReserveAccounts: false,
    averageMonthlyCost: average,
    costSource: manualMonthlyCost ? 'MANUAL' : history === null ? 'NONE' : 'HISTORY',
    months: cents === null ? null : 0,
    targetMonths,
    missing: cents === null ? null : money(cents * BigInt(targetMonths), 'BRL'),
    manualMonthlyCost,
  }
}

/** The last day of the month `months` after the one `iso` is in, as `YYYY-MM-DD`. */
function monthEnd(iso: string, months: number): string {
  const [year, month] = iso.split('-').map(Number)
  return new Date(Date.UTC(year, month + months, 0)).toISOString().slice(0, 10)
}

/** The projection as the mock knows it: nothing scheduled; with a history the balance falls by its average each month (D13). */
function projectionView(workspace: MeWorkspace): Schemas['ProjectionView'] {
  const today = familyToday(workspace)
  const balance = money(
    mockState.accounts
      .filter((account) => account.archivedAt === null)
      .reduce((sum, account) => sum + balanceCents(account), 0n),
    'BRL',
  )
  const history = mockState.spendingHistoryCents
  const balanceCentsToday = parseCents(balance.amount)
  return {
    today,
    balanceToday: balance,
    hasHistory: history !== null,
    averageSpending: history === null ? null : money(history, 'BRL'),
    points: [0, 1, 2, 3].map((months) => ({
      monthEnd: monthEnd(today, months),
      balance: money(balanceCentsToday - (history ?? 0n) * BigInt(months), 'BRL'),
      scheduled: money(0n, 'BRL'),
      estimatedSpending: money(history === null ? 0n : history * BigInt(months), 'BRL'),
    })),
  }
}

/** The month's confirmed spending: fixed when it came from a rule or its category (or the parent) is fixed (D14). */
function spendingSplitView(month: string): Schemas['SpendingSplitView'] {
  const byId = new Map(mockState.categories.map((category) => [category.id, category]))
  const fixedCategory = (id: string | null) => {
    const category = id ? byId.get(id) : undefined
    if (!category) return false
    const parent = category.parentId ? byId.get(category.parentId) : undefined
    return category.isFixed || parent?.isFixed === true
  }
  let fixed = 0n
  let variable = 0n
  for (const transaction of activeTransactions()) {
    if (transaction.kind !== 'EXPENSE' || !transaction.occurredOn.startsWith(`${month}-`)) continue
    const cents = parseCents(transaction.amount.amount)
    if (transaction.recurringRuleId !== null || fixedCategory(transaction.categoryId))
      fixed += cents
    else variable += cents
  }
  return { month, fixed: money(fixed, 'BRL'), variable: money(variable, 'BRL') }
}

/** Not zero (above zero unless `signed`), at most 999,999,999.99 and with at most 2 decimal places. */
function goalCents(value: unknown, signed = false): bigint | undefined {
  const read = readMoney(value)
  if (!read) return undefined
  const abs = read.cents < 0n ? -read.cents : read.cents
  if (abs === 0n || abs > MAX_CENTS || (!signed && read.cents < 0n)) return undefined
  return read.cents
}

/** Whether the free plan already holds its 3 active goals (the API's D11, with billing on as in staging). */
const goalLimitReachedNow = () =>
  mockState.billing.plan.key === 'FREE' &&
  mockState.goals.filter((goal) => !goal.archived && goal.achievedAt === null).length >=
    FREE_ACTIVE_GOALS

/** The goal as the API shows it; a reached goal gets its `achievedAt` here, as the API's read does. */
function goalView(goal: MockGoal, workspace: MeWorkspace): Schemas['GoalView'] {
  const account = goal.linkedAccountId
    ? mockState.accounts.find((each) => each.id === goal.linkedAccountId)
    : undefined
  const raw = account
    ? balanceCents(account)
    : goal.contributions.reduce((sum, each) => sum + parseCents(each.amount.amount), 0n)
  const saved = raw < 0n ? 0n : raw
  if (saved >= goal.targetCents && goal.achievedAt === null) {
    goal.achievedAt = new Date().toISOString()
  }
  const percent = saved >= goal.targetCents ? 100 : Number((saved * 100n) / goal.targetCents)
  let suggestedMonthly: ApiMoney | null = null
  if (goal.dueOn && saved < goal.targetCents) {
    const [year, month] = thisMonth().split('-').map(Number)
    const [dueYear, dueMonth] = goal.dueOn.split('-').map(Number)
    const months = BigInt(Math.max(1, (dueYear - year) * 12 + (dueMonth - month)))
    suggestedMonthly = money((goal.targetCents - saved + months - 1n) / months, goal.currency)
  }
  return {
    id: goal.id,
    name: goal.name,
    icon: goal.icon,
    targetAmount: money(goal.targetCents, goal.currency),
    saved: money(saved, goal.currency),
    percent,
    dueOn: goal.dueOn,
    suggestedMonthly,
    linked: goal.linkedAccountId !== null,
    linkedAccountId: workspace.role === 'YOUTH' ? null : goal.linkedAccountId,
    achievedAt: goal.achievedAt,
    version: goal.version,
  }
}

/** A goal of the family that is not archived, or the 404 the API answers. */
function activeGoal(id: string): MockGoal | Response {
  return mockState.goals.find((goal) => goal.id === id && !goal.archived) ?? goalNotFound()
}

/** The line of a limit: what the category (and its subcategories) spent in the month, confirmed only. */
function budgetLine(budget: MockBudget): { name: string; line: Schemas['BudgetLineView'] } {
  const currency = 'BRL'
  const category = mockState.categories.find((each) => each.id === budget.categoryId)
  const spentCents = activeTransactions()
    .filter((transaction) => {
      if (transaction.kind !== 'EXPENSE' || !transaction.occurredOn.startsWith(budget.month)) {
        return false
      }
      const own = mockState.categories.find((each) => each.id === transaction.categoryId)
      return own?.id === budget.categoryId || own?.parentId === budget.categoryId
    })
    .reduce((sum, transaction) => sum + parseCents(transaction.amount.amount), 0n)
  const percent = Number((spentCents * 100n) / budget.limitCents)
  const state =
    spentCents > budget.limitCents
      ? 'OVER'
      : spentCents === budget.limitCents
        ? 'AT'
        : percent >= 80
          ? 'NEAR'
          : 'UNDER'
  return {
    name: category?.name ?? '',
    line: {
      categoryId: budget.categoryId,
      limit: money(budget.limitCents, currency),
      spent: money(spentCents, currency),
      usagePercent: percent,
      state,
      overBy: state === 'OVER' ? money(spentCents - budget.limitCents, currency) : null,
      version: budget.version,
    },
  }
}

/** A Youth sees only the rules on the accounts that are theirs. */
function ruleVisible(workspace: MeWorkspace, rule: MockRule): boolean {
  if (workspace.role !== 'YOUTH') return true
  return (
    mockState.accounts.find((account) => account.id === rule.accountId)?.ownerMembershipId ===
    workspace.membershipId
  )
}

function ruleView(rule: MockRule): Schemas['RecurringRuleView'] {
  const view: Partial<MockRule> = { ...rule }
  delete view.archived
  return view as Schemas['RecurringRuleView']
}

/** The fields a create or an edit sets, as stored. */
function ruleFields(body: RuleBody) {
  const transfer = body.kind === 'TRANSFER'
  return {
    kind: body.kind!,
    amount: money(validCents(String(body.amount!.amount))!, body.amount!.currency),
    accountId: body.accountId!,
    toAccountId: transfer ? (body.toAccountId ?? null) : null,
    categoryId: transfer ? null : (body.categoryId ?? null),
    description: body.description?.trim() || null,
    paymentMethod: transfer ? null : (body.paymentMethod ?? null),
    frequency: body.frequency!,
    interval: body.interval ?? 1,
    startOn: body.startOn!,
    endOn: body.endOn ?? null,
  }
}

function ruleFailure(body: RuleBody | undefined): Response | undefined {
  if (typeof body !== 'object' || body === null) return malformed()
  const errors: FieldError[] = (['kind', 'amount', 'accountId', 'frequency', 'startOn'] as const)
    .filter((field) => body[field] == null)
    .map((field) => ({ field, code: 'NotNull' }))
  if (errors.length > 0) return invalid(errors)
  if (!KINDS.includes(body.kind) || !FREQUENCIES.includes(body.frequency)) return malformed()
  if (!DATE.test(body.startOn!) || (body.endOn != null && !DATE.test(body.endOn))) {
    return malformed()
  }
  if (validCents(String(body.amount!.amount)) === undefined) {
    return invalid([{ field: 'amount', code: 'TransactionAmount' }])
  }
  const interval = body.interval ?? 1
  if (!Number.isInteger(interval) || interval < 1 || interval > 12) {
    return invalid([{ field: 'interval', code: 'Range' }])
  }
  if (body.endOn != null && body.endOn < body.startOn!) {
    return invalid([{ field: 'endOn', code: 'RecurringRuleEnd' }])
  }
  const account = mockState.accounts.find((each) => each.id === body.accountId)
  if (!account || account.archivedAt !== null) {
    return rejected('recurring-rule.account-not-found', 'Essa conta não está disponível.')
  }
  if (body.kind === 'TRANSFER') {
    const to = mockState.accounts.find((each) => each.id === body.toAccountId)
    if (!to || to.archivedAt !== null || to.id === account.id) {
      return rejected('recurring-rule.account-not-found', 'Essa conta não está disponível.')
    }
  }
  return undefined
}

/** The first date of the rule on or after `today`, stepping by its interval from `startOn`. */
function nextDue(rule: MockRule, today: string): string | null {
  const [year, month, day] = rule.startOn.split('-').map(Number)
  for (let step = 0; step < 5000; step += 1) {
    const offset = step * rule.interval
    let date: Date
    if (rule.frequency === 'WEEKLY') {
      date = new Date(Date.UTC(year, month - 1, day + offset * 7))
    } else {
      const months = rule.frequency === 'MONTHLY' ? offset : offset * 12
      const first = new Date(Date.UTC(year, month - 1 + months, 1))
      const last = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0))
      date = new Date(
        Date.UTC(first.getUTCFullYear(), first.getUTCMonth(), Math.min(day, last.getUTCDate())),
      )
    }
    const iso = date.toISOString().slice(0, 10)
    if (rule.endOn !== null && iso > rule.endOn) return null
    if (iso >= today) return iso
  }
  return null
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

  // The live stream is not mocked: a 204 makes the browser's EventSource give up quietly.
  http.get(url('/v1/workspaces/:ws/events'), () => new HttpResponse(null, { status: 204 })),

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

  // Public: no session needed, and never cached by the browser, like LegalController.java.
  http.get(url('/v1/legal/current'), () =>
    HttpResponse.json(
      { terms: mockState.currentDocuments.TERMS, privacy: mockState.currentDocuments.PRIVACY },
      { headers: { 'Cache-Control': 'no-store' } },
    ),
  ),

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
          acceptanceRequired: !keepsAcceptance(
            mockState.currentDocuments[purpose],
            accepted?.version,
          ),
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
    let recorded = false
    for (const purpose of ['TERMS', 'PRIVACY'] as const) {
      const accepted = mockState.acceptedDocuments[purpose]
      if (accepted?.version !== mockState.currentDocuments[purpose]) {
        mockState.acceptedDocuments[purpose] = {
          version: mockState.currentDocuments[purpose],
          at: now,
        }
        recorded = true
      }
    }
    // Like the API, only an accept that changed something reaches the history.
    if (recorded) {
      mockState.activity.unshift({
        id: crypto.randomUUID(),
        action: 'documents.accepted',
        occurredAt: now,
        workspaceId: null,
        actorName: mockState.user.displayName,
        byMe: true,
        metadata: {},
      })
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

  http.post(url('/v1/workspaces/:ws/billing/checkout'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER') return roleDenied()
    const { billing } = mockState
    if (billing.plan.key === 'FAMILY') {
      return problem(409, 'billing.plan-active', 'Sua família já tem um plano ativo.')
    }
    // One order waits for payment at a time: a new checkout cancels the earlier one.
    for (const order of billing.orders) {
      if (order.workspaceId === workspace.id && order.status === 'CREATED')
        order.status = 'CANCELED'
    }
    const id = crypto.randomUUID()
    const checkoutUrl = `https://checkout.e2e.test/c/chk_fake_${id}`
    billing.orders.push({ id, workspaceId: workspace.id, status: 'CREATED', checkoutUrl })
    const view: Schemas['CheckoutView'] = { orderId: id, checkoutUrl }
    return HttpResponse.json(view, { status: 201 })
  }),

  http.post(url('/v1/workspaces/:ws/billing/orders/:id/refresh'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    const order = mockState.billing.orders.find(
      (candidate) => candidate.id === params.id && candidate.workspaceId === workspace.id,
    )
    if (!order) return problem(404, 'billing.order-not-found', 'Não encontramos esse pedido.')
    // The mock has no payment provider: the order is shown as it stands.
    const view: Schemas['OrderView'] = {
      id: order.id,
      status: order.status,
      refundReason: null,
    }
    return HttpResponse.json(view)
  }),

  http.post(url('/v1/workspaces/:ws/billing/orders/:id/refund'), ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER') return roleDenied()
    const { billing } = mockState
    const order = billing.orders.find(
      (candidate) => candidate.id === params.id && candidate.workspaceId === workspace.id,
    )
    if (!order) return problem(404, 'billing.order-not-found', 'Não encontramos esse pedido.')
    if (order.status !== 'PAID') {
      return problem(409, 'billing.refund-not-allowed', 'Esse pedido não pode ser reembolsado.')
    }
    // The plan ends at once; the refund itself is the gateway's business.
    order.status = 'REFUND_PENDING'
    billing.plan = { key: 'FREE', endsAt: null }
    const view: Schemas['OrderView'] = {
      id: order.id,
      status: order.status,
      refundReason: 'CUSTOMER',
    }
    return HttpResponse.json(view, { status: 202 })
  }),

  http.get(url('/v1/workspaces/:ws/billing/plan'), ({ params }) => {
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    const { billing } = mockState
    const running = billing.plan.key === 'FAMILY'
    const owned = billing.orders.filter((order) => order.workspaceId === workspace.id)
    const paid = running ? owned.find((order) => order.status === 'PAID') : undefined
    const open = owned.find(
      (order) => order.status === 'CREATED' || order.status === 'REFUND_PENDING',
    )
    const view: Schemas['PlanDetails'] = {
      key: running ? 'FAMILY' : 'FREE',
      founder: running,
      endsAt: running ? billing.plan.endsAt : null,
      theme: running ? (billing.plan.theme ?? 'TEAL') : 'TEAL',
      presaleOpen: true,
      canPurchase: workspace.role === 'OWNER' && !running,
      canRefund: workspace.role === 'OWNER' && paid !== undefined,
      importAllowed: importAllowed(),
      refundDeadline:
        workspace.role === 'OWNER' && paid !== undefined ? refundDeadline(Date.now()) : null,
      paidOrderId: paid?.id ?? null,
      openOrder: open ? { id: open.id, status: open.status, refundReason: null } : null,
    }
    return HttpResponse.json(view)
  }),

  http.put(url('/v1/workspaces/:ws/billing/plan/theme'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(String(params.ws))
    if (workspace instanceof Response) return workspace
    if (workspace.role !== 'OWNER') return roleDenied()
    const { billing } = mockState
    if (billing.plan.key !== 'FAMILY') {
      return problem(403, 'plan.required', 'Este recurso é do plano Família.')
    }
    const body = (await request.json()) as Schemas['ThemeRequest']
    billing.plan = { ...billing.plan, theme: body.theme }
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
      isReserve: false,
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
        (body.name != null && typeof body.name !== 'string') ||
        (body.isReserve != null && typeof body.isReserve !== 'boolean')
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
      if (
        name === null &&
        body.type == null &&
        initial === null &&
        balance === null &&
        body.isReserve == null
      ) {
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
      if (typeof body.isReserve === 'boolean') account.isReserve = body.isReserve
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
      isFixed: false,
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
        (color != null && !CATEGORY_COLORS.includes(color)) ||
        (body.isFixed != null && typeof body.isFixed !== 'boolean')
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
      if (name === null && icon == null && color == null && body.isFixed == null) {
        errors.push({ field: 'notEmpty', code: 'AssertTrue' })
      }
      if (errors.length > 0) return invalid(errors)
      const category = mockState.categories.find((existing) => existing.id === params.id)
      if (!category) return categoryNotFound()
      if (body.isFixed === true && category.kind !== 'EXPENSE') {
        return rejected('category.fixed-not-expense', 'Só categorias de gasto podem ser fixas.')
      }
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
      if (typeof body.isFixed === 'boolean') category.isFixed = body.isFixed
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

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/tags'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    return HttpResponse.json(
      mockState.tags
        .filter((tag) => tag.archivedAt === null)
        .sort((x, y) => tagKey(x.name).localeCompare(tagKey(y.name))),
    )
  }),

  http.patch<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/tags/:id'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as { name?: unknown } | undefined
      if (typeof body !== 'object' || body === null) return malformed()
      if (body.name === undefined || tagErrors([body.name]).length > 0) {
        return invalid([{ field: 'name', code: 'Size' }])
      }
      const tag = mockState.tags.find((each) => each.id === params.id && each.archivedAt === null)
      if (!tag) return tagNotFound()
      const name = (body.name as string).trim()
      if (
        mockState.tags.some(
          (each) =>
            each.id !== tag.id && each.archivedAt === null && tagKey(each.name) === tagKey(name),
        )
      ) {
        return problem(409, 'tag.name-taken', 'Já existe uma tag com esse nome.')
      }
      // A transaction shows its tags by name, so the new name follows the tag everywhere.
      for (const transaction of mockState.transactions) {
        transaction.tags = transaction.tags
          .map((each) => (each === tag.name ? name : each))
          .sort((x, y) => tagKey(x).localeCompare(tagKey(y)))
      }
      tag.name = name
      return HttpResponse.json(tag)
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/tags/:id/archive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!UUID.test(params.id)) return malformed()
      const tag = mockState.tags.find((each) => each.id === params.id)
      if (!tag) return tagNotFound()
      // Archiving again changes nothing; the tag stays on the transactions that have it.
      tag.archivedAt ??= new Date().toISOString()
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/transactions'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const search = new URL(request.url).searchParams
    const month = search.get('month') ?? ''
    const categoryId = search.get('categoryId')
    const accountId = search.get('accountId')
    const tagId = search.get('tagId')
    const paymentMethod = search.get('paymentMethod')
    if (!MONTH.test(month)) return malformed()
    if ([categoryId, accountId, tagId].some((id) => id !== null && !UUID.test(id)))
      return malformed()
    const outOfRange = monthOutOfRange(workspace, month)
    if (outOfRange) return outOfRange
    // A category takes its subcategories along; an account, what leaves it and what enters it.
    const inCategory = new Set(
      mockState.categories
        .filter((category) => category.id === categoryId || category.parentId === categoryId)
        .map((category) => category.id),
    )
    const tagName = mockState.tags.find((tag) => tag.id === tagId)?.name
    const planning = mockState.modules[workspace.id]?.planning === true
    const thisMonth = familyToday(workspace).startsWith(`${month}-`)
    return HttpResponse.json(
      listedTransactions()
        .filter(
          (transaction) =>
            // A planned one shows only with the module on: the month's, and in this month the overdue.
            (transaction.status === 'CONFIRMED'
              ? transaction.occurredOn.startsWith(`${month}-`)
              : planning &&
                (transaction.occurredOn.startsWith(`${month}-`) ||
                  (thisMonth && transaction.occurredOn < `${month}-01`))) &&
            (paymentMethod === null || transaction.paymentMethod === paymentMethod) &&
            (tagId === null ||
              (tagName !== undefined && transaction.tags.some((name) => name === tagName))) &&
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
    errors.push(...tagErrors(body.tags))
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
      paymentMethod: body.paymentMethod ?? null,
      tags: tagNamesOf(body.tags ?? []),
      status: 'CONFIRMED',
      scheduledOn: null,
      recurringRuleId: null,
      importKey: null,
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
    if (overTagLimit(transaction.tags)) return tagLimit()
    makeTags(transaction.tags)
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
      errors.push(...tagErrors(body.tags))
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
        paymentMethod: body.paymentMethod ?? null,
        // No tags keeps the ones it has; a list, even an empty one, replaces them.
        tags: body.tags == null ? current.tags : tagNamesOf(body.tags),
        memberId: body.memberId ?? current.memberId,
        version: current.version + 1,
      }
      const failure = familyRuleFailure(workspace, edited, current)
      if (failure) return failure
      if (overTagLimit(edited.tags)) return tagLimit()
      makeTags(edited.tags)
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

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/recurring-rules'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    return HttpResponse.json(
      mockState.rules
        .filter((rule) => !rule.archived && ruleVisible(workspace, rule))
        .sort(
          (a, b) =>
            (a.nextDueOn ?? '9999-12-31').localeCompare(b.nextDueOn ?? '9999-12-31') ||
            a.id.localeCompare(b.id),
        )
        .map(ruleView),
    )
  }),

  http.post<{ ws: string }>(
    url('/v1/workspaces/:ws/recurring-rules'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      const body = (await readJson(request)) as RuleBody | undefined
      const failure = ruleFailure(body)
      if (failure || !body) return failure
      const rule: MockRule = {
        id: crypto.randomUUID(),
        ...ruleFields(body),
        createdBy: mockState.user.id,
        nextDueOn: null,
        version: 0,
        archived: false,
      }
      rule.nextDueOn = nextDue(rule, familyToday(workspace))
      mockState.rules.push(rule)
      // The API plans the first occurrence at once.
      if (rule.nextDueOn) {
        mockState.transactions.push({
          id: crypto.randomUUID(),
          kind: rule.kind,
          amount: rule.amount,
          occurredOn: rule.nextDueOn,
          accountId: rule.accountId,
          toAccountId: rule.toAccountId,
          categoryId: rule.categoryId,
          description: rule.description,
          paymentMethod: rule.paymentMethod,
          tags: [],
          status: 'PLANNED',
          scheduledOn: rule.nextDueOn,
          recurringRuleId: rule.id,
          importKey: null,
          createdAt: new Date().toISOString(),
          memberId: workspace.membershipId,
          version: 0,
        })
      }
      return HttpResponse.json(ruleView(rule), { status: 201 })
    },
  ),

  http.patch<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/recurring-rules/:id'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as (RuleBody & { version?: number }) | undefined
      const rule = mockState.rules.find((each) => each.id === params.id && !each.archived)
      if (!rule) return ruleNotFound()
      const failure = ruleFailure(body)
      if (failure || !body) return failure
      if (typeof body.version !== 'number') return malformed()
      if (body.version !== rule.version) {
        return problem(
          409,
          'recurring-rule.version-conflict',
          'Essa recorrência mudou. Confira a lista.',
        )
      }
      Object.assign(rule, ruleFields(body))
      rule.nextDueOn = nextDue(rule, familyToday(workspace))
      rule.version += 1
      return HttpResponse.json(ruleView(rule))
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/recurring-rules/:id/archive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      if (!UUID.test(params.id)) return malformed()
      const rule = mockState.rules.find((each) => each.id === params.id)
      if (!rule) return ruleNotFound()
      // Archiving again changes nothing; what it planned and is still open goes away.
      if (!rule.archived) {
        rule.archived = true
        for (const transaction of mockState.transactions) {
          if (transaction.recurringRuleId === rule.id && transaction.status === 'PLANNED') {
            mockState.deletedTransactionIds.add(transaction.id)
          }
        }
      }
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/budgets'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    if (workspace.role === 'YOUTH') return roleDenied()
    const month = new URL(request.url).searchParams.get('month')
    if (!month || !MONTH.test(month)) return budgetInvalid()
    const lines = mockState.budgets
      .filter((budget) => budget.month === month)
      .map((budget) => budgetLine(budget))
      .sort(
        (a, b) =>
          a.name.localeCompare(b.name) || a.line.categoryId.localeCompare(b.line.categoryId),
      )
      .map((entry) => entry.line)
    return HttpResponse.json({ month, lines })
  }),

  http.put<{ ws: string; month: string; categoryId: string }>(
    url('/v1/workspaces/:ws/budgets/:month/:categoryId'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      if (!MONTH.test(params.month)) return budgetInvalid()
      const body = (await readJson(request)) as
        { limitAmount?: ApiMoney; version?: number | null } | undefined
      const category = mockState.categories.find((each) => each.id === params.categoryId)
      if (!category) return problem(404, 'category.not-found', 'Categoria não encontrada.')
      if (category.kind !== 'EXPENSE') {
        return problem(
          422,
          'budget.category-not-expense',
          'Orçamento só vale para categorias de gasto.',
        )
      }
      let limitCents: bigint
      try {
        limitCents = parseCents(body?.limitAmount?.amount ?? '')
      } catch {
        return budgetInvalid()
      }
      if (limitCents <= 0n) return budgetInvalid()
      const stored = mockState.budgets.find(
        (each) => each.month === params.month && each.categoryId === params.categoryId,
      )
      const version = body?.version ?? null
      if (!stored) {
        if (version !== null) return budgetConflict()
        const created = {
          month: params.month,
          categoryId: params.categoryId,
          limitCents,
          version: 0,
        }
        mockState.budgets.push(created)
        return HttpResponse.json(budgetLine(created).line, { status: 201 })
      }
      if (version !== stored.version) return budgetConflict()
      stored.limitCents = limitCents
      stored.version += 1
      return HttpResponse.json(budgetLine(stored).line)
    },
  ),

  http.delete<{ ws: string; month: string; categoryId: string }>(
    url('/v1/workspaces/:ws/budgets/:month/:categoryId'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      if (!MONTH.test(params.month)) return budgetInvalid()
      mockState.budgets = mockState.budgets.filter(
        (each) => !(each.month === params.month && each.categoryId === params.categoryId),
      )
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.post<{ ws: string; month: string }>(
    url('/v1/workspaces/:ws/budgets/:month/copy-previous'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      if (!MONTH.test(params.month)) return budgetInvalid()
      const [year, month] = params.month.split('-').map(Number)
      const previous =
        month === 1 ? `${year - 1}-12` : `${year}-${String(month - 1).padStart(2, '0')}`
      const copies = mockState.budgets.filter(
        (from) =>
          from.month === previous &&
          mockState.categories.some(
            (each) => each.id === from.categoryId && each.archivedAt === null,
          ) &&
          !mockState.budgets.some(
            (to) => to.month === params.month && to.categoryId === from.categoryId,
          ),
      )
      for (const from of copies) {
        mockState.budgets.push({ ...from, month: params.month, version: 0 })
      }
      return HttpResponse.json({ copied: copies.length })
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/projection'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    if (workspace.role === 'YOUTH') return roleDenied()
    return HttpResponse.json(projectionView(workspace))
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/spending-split'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    if (workspace.role === 'YOUTH') return roleDenied()
    const month = new URL(request.url).searchParams.get('month')
    if (!month || !MONTH.test(month)) return budgetInvalid()
    return HttpResponse.json(spendingSplitView(month))
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/reserve'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    if (workspace.role === 'YOUTH') return roleDenied()
    return HttpResponse.json(reserveView())
  }),

  http.patch<{ ws: string }>(url('/v1/workspaces/:ws/reserve'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    if (!canManageRules(workspace)) return roleDenied()
    const body = (await readJson(request)) as Schemas['UpdateReserveRequest'] | undefined
    if (!body || typeof body !== 'object') return malformed()
    const hasCost = 'manualMonthlyCost' in body
    const cost = hasCost ? readMoney(body.manualMonthlyCost) : null
    if (
      (body.targetMonths === undefined && !hasCost) ||
      (body.targetMonths != null &&
        (!Number.isInteger(body.targetMonths) ||
          body.targetMonths < 1 ||
          body.targetMonths > 24)) ||
      cost === undefined ||
      (cost !== null && (cost.cents <= 0n || cost.cents > MAX_CENTS))
    ) {
      return budgetInvalid()
    }
    if (body.targetMonths != null) mockState.reserve.targetMonths = body.targetMonths
    if (hasCost) {
      mockState.reserve.manualMonthlyCost = cost ? money(cost.cents, cost.currency) : null
    }
    return HttpResponse.json(reserveView())
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/indicators'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (mockState.modules[workspace.id]?.indicators !== true) {
      return problem(403, 'module.disabled', 'O módulo de indicadores está desligado.')
    }
    return HttpResponse.json({ indicators: mockState.indicators })
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/goals'), ({ params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    const views = mockState.goals
      .filter((goal) => !goal.archived)
      .map((goal) => goalView(goal, workspace))
    const active = views.filter((view) => view.achievedAt === null)
    const achieved = views
      .filter((view) => view.achievedAt !== null)
      .sort((a, b) => (b.achievedAt ?? '').localeCompare(a.achievedAt ?? ''))
    return HttpResponse.json({
      goals: [...active, ...achieved],
      limitReached: canManageRules(workspace) && goalLimitReachedNow(),
    })
  }),

  http.post<{ ws: string }>(url('/v1/workspaces/:ws/goals'), async ({ request, params }) => {
    if (withoutToken(request)) return csrfInvalid()
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    if (!planningOn(workspace)) return planningOff()
    if (!canManageRules(workspace)) return roleDenied()
    const body = (await readJson(request)) as Partial<Schemas['CreateGoalRequest']> | undefined
    if (!body || typeof body !== 'object') return malformed()
    const name = typeof body.name === 'string' ? body.name.trim() : ''
    const targetCents = goalCents(body.targetAmount)
    if (
      !name ||
      name.length > MAX_GOAL_NAME_LENGTH ||
      !GOAL_ICONS.includes(body.icon) ||
      targetCents === undefined ||
      (body.dueOn != null && !MONTH.test(body.dueOn))
    ) {
      return budgetInvalid()
    }
    if (body.targetAmount?.currency !== workspace.currency) {
      return goalRejected('goal.currency', 'A meta precisa estar na moeda da família.')
    }
    if (body.dueOn != null && body.dueOn < thisMonth()) {
      return goalRejected('goal.due-in-past', 'O prazo não pode ser um mês que já passou.')
    }
    const linked = body.linkedAccountId ?? null
    if (
      linked !== null &&
      !mockState.accounts.some((each) => each.id === linked && !each.archivedAt)
    ) {
      return goalRejected(
        'goal.linked-account-invalid',
        'Escolha uma conta ativa da família para a meta acompanhar.',
      )
    }
    if (goalLimitReachedNow()) return goalLimitReached()
    const goal: MockGoal = {
      id: crypto.randomUUID(),
      name,
      icon: body.icon as string,
      targetCents,
      currency: workspace.currency,
      dueOn: body.dueOn ?? null,
      linkedAccountId: linked,
      achievedAt: null,
      archived: false,
      version: 0,
      contributions: [],
    }
    mockState.goals.push(goal)
    return HttpResponse.json(goalView(goal, workspace), { status: 201 })
  }),

  http.patch<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/goals/:id'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      const goal = activeGoal(params.id)
      if (goal instanceof Response) return goal
      const body = (await readJson(request)) as Partial<Schemas['UpdateGoalRequest']> | undefined
      if (!body || typeof body !== 'object') return malformed()
      const name = typeof body.name === 'string' ? body.name.trim() : null
      const targetCents = body.targetAmount == null ? null : goalCents(body.targetAmount)
      if (
        typeof body.version !== 'number' ||
        (name !== null && (!name || name.length > MAX_GOAL_NAME_LENGTH)) ||
        (body.icon != null && !GOAL_ICONS.includes(body.icon)) ||
        (body.dueOn != null && !MONTH.test(body.dueOn)) ||
        targetCents === undefined ||
        (body.clearDueOn === true && body.dueOn != null) ||
        (name === null &&
          targetCents === null &&
          body.dueOn == null &&
          body.icon == null &&
          body.clearDueOn !== true)
      ) {
        return budgetInvalid()
      }
      if (body.version !== goal.version) {
        return problem(
          409,
          'goal.version-conflict',
          'A meta foi alterada por outra pessoa. Recarregue e tente de novo.',
        )
      }
      if (body.targetAmount != null && body.targetAmount.currency !== goal.currency) {
        return goalRejected('goal.currency', 'A meta precisa estar na moeda da família.')
      }
      if (body.dueOn != null && body.dueOn < thisMonth()) {
        return goalRejected('goal.due-in-past', 'O prazo não pode ser um mês que já passou.')
      }
      if (name !== null) goal.name = name
      if (targetCents !== null) goal.targetCents = targetCents
      if (body.clearDueOn === true) goal.dueOn = null
      else if (body.dueOn != null) goal.dueOn = body.dueOn
      if (body.icon != null) goal.icon = body.icon
      goal.version += 1
      return HttpResponse.json(goalView(goal, workspace))
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/goals/:id/archive'),
    ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      const goal = mockState.goals.find((each) => each.id === params.id)
      if (!goal) return goalNotFound()
      goal.archived = true
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/goals/:id/contributions'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      if (!canManageRules(workspace)) return roleDenied()
      const goal = activeGoal(params.id)
      if (goal instanceof Response) return goal
      const body = (await readJson(request)) as
        Partial<Schemas['CreateContributionRequest']> | undefined
      if (!body || typeof body !== 'object') return malformed()
      const cents = goalCents(body.amount, true)
      if (
        cents === undefined ||
        typeof body.occurredOn !== 'string' ||
        !DATE.test(body.occurredOn)
      ) {
        return budgetInvalid()
      }
      if (goal.linkedAccountId !== null) {
        return goalRejected('goal.linked-account', 'Esta meta acompanha o saldo de uma conta.')
      }
      if (body.amount?.currency !== goal.currency) {
        return goalRejected('goal.currency', 'A meta precisa estar na moeda da família.')
      }
      const sum = goal.contributions.reduce(
        (total, each) => total + parseCents(each.amount.amount),
        0n,
      )
      if (sum + cents < 0n) {
        return goalRejected('goal.negative-balance', 'O guardado não pode ficar negativo.')
      }
      const contribution: Schemas['ContributionView'] = {
        id: crypto.randomUUID(),
        amount: money(cents, goal.currency),
        occurredOn: body.occurredOn,
        memberId: workspace.membershipId,
      }
      goal.contributions.unshift(contribution)
      // Marks the goal reached when this contribution got it there, as the API does.
      goalView(goal, workspace)
      return HttpResponse.json(contribution, { status: 201 })
    },
  ),

  http.get<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/goals/:id/contributions'),
    ({ params }) => {
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (!planningOn(workspace)) return planningOff()
      const goal = activeGoal(params.id)
      if (goal instanceof Response) return goal
      return HttpResponse.json(goal.contributions)
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/transactions/:id/confirm'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (workspace.role === 'VIEWER') return roleDenied()
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as
        | {
            version?: number
            amount?: ApiMoney
            occurredOn?: string | null
            accountId?: string | null
          }
        | undefined
      if (typeof body !== 'object' || body === null || typeof body.version !== 'number') {
        return malformed()
      }
      const stored = storedTransaction(params.id)
      if (!stored || stored.deleted) return transactionNotFound()
      const { transaction } = stored
      if (transaction.status !== 'PLANNED') {
        return problem(409, 'transaction.not-planned', 'Esse lançamento não é um previsto.')
      }
      if (body.version !== transaction.version) {
        return problem(409, 'transaction.version-conflict', 'Esse previsto mudou. Confira a lista.')
      }
      const cents = body.amount === undefined ? undefined : validCents(String(body.amount.amount))
      if (body.amount !== undefined && cents === undefined) {
        return invalid([{ field: 'amount', code: 'TransactionAmount' }])
      }
      if (body.occurredOn != null && !DATE.test(body.occurredOn)) return malformed()
      if (body.amount !== undefined && cents !== undefined) {
        transaction.amount = money(cents, body.amount.currency)
      }
      if (body.occurredOn) transaction.occurredOn = body.occurredOn
      if (body.accountId) transaction.accountId = body.accountId
      transaction.status = 'CONFIRMED'
      transaction.memberId = workspace.membershipId
      transaction.version += 1
      return HttpResponse.json(transaction)
    },
  ),

  http.post<{ ws: string; id: string }>(
    url('/v1/workspaces/:ws/transactions/:id/skip'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (workspace.role === 'VIEWER') return roleDenied()
      if (!UUID.test(params.id)) return malformed()
      const body = (await readJson(request)) as { version?: number } | undefined
      if (typeof body !== 'object' || body === null || typeof body.version !== 'number') {
        return malformed()
      }
      const stored = storedTransaction(params.id)
      if (!stored || stored.deleted) return transactionNotFound()
      if (stored.transaction.status !== 'PLANNED') {
        return problem(409, 'transaction.not-planned', 'Esse lançamento não é um previsto.')
      }
      if (body.version !== stored.transaction.version) {
        return problem(409, 'transaction.version-conflict', 'Esse previsto mudou. Confira a lista.')
      }
      mockState.deletedTransactionIds.add(params.id)
      stored.transaction.version += 1
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/dashboard'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const search = new URL(request.url).searchParams
    const month = search.get('month') ?? ''
    if (!MONTH.test(month)) return malformed()
    const outOfRange = monthOutOfRange(workspace, month)
    if (outOfRange) return outOfRange
    return HttpResponse.json(dashboardFor(month, workspace.currency, search.get('paymentMethod')))
  }),

  // The category the family last used for a description: here, Mercado for "padaria pao quente".
  http.post<{ ws: string }>(
    url('/v1/workspaces/:ws/imports/category-hints'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (workspace.role === 'VIEWER') return roleDenied()
      if (!importAllowed()) return problem(403, 'plan.required', 'Este recurso é do plano Família.')
      const body = (await readJson(request)) as Partial<Schemas['CategoryHintsRequest']> | undefined
      if (typeof body !== 'object' || body === null || !Array.isArray(body.items)) {
        return malformed()
      }
      const mercado = mockState.categories.find(
        (category) =>
          category.kind === 'EXPENSE' &&
          category.name === 'Mercado' &&
          category.archivedAt === null,
      )
      const view: Schemas['CategoryHintsResponse'] = {
        hints: body.items.map((item) => ({
          categoryId:
            item.kind === 'EXPENSE' && item.key === HINTED_KEY ? (mercado?.id ?? null) : null,
        })),
      }
      return HttpResponse.json(view)
    },
  ),

  // A key already seen in the session is skipped; a line naming a planned one confirms it with planning on.
  http.post<{ ws: string; accountId: string }>(
    url('/v1/workspaces/:ws/accounts/:accountId/imports'),
    async ({ request, params }) => {
      if (withoutToken(request)) return csrfInvalid()
      const workspace = guard(params.ws)
      if (workspace instanceof Response) return workspace
      if (workspace.role === 'VIEWER') return roleDenied()
      if (!importAllowed()) return problem(403, 'plan.required', 'Este recurso é do plano Família.')
      if (!UUID.test(params.accountId)) return malformed()
      const account = mockState.accounts.find(
        (each) => each.id === params.accountId && each.archivedAt === null,
      )
      if (!account) return problem(404, 'account.not-found', 'Conta não encontrada.')
      const body = (await readJson(request)) as Partial<Schemas['ImportRequest']> | undefined
      if (
        typeof body !== 'object' ||
        body === null ||
        !Array.isArray(body.rows) ||
        body.rows.length === 0 ||
        body.rows.length > 500
      ) {
        return malformed()
      }
      const amounts = body.rows.map((row) => validCents(String(row.amount?.amount)))
      if (amounts.some((cents) => cents === undefined)) {
        return invalid([{ field: 'rows', code: 'TransactionAmount' }])
      }
      const planning = mockState.modules[workspace.id]?.planning === true
      const taken = new Set(
        mockState.transactions.flatMap((transaction) =>
          transaction.importKey ? [transaction.importKey] : [],
        ),
      )
      const result: Schemas['ImportResult'] = { created: 0, confirmed: 0, skipped: 0 }
      body.rows.forEach((row, index) => {
        const cents = amounts[index]!
        if (mockState.importKeys.has(row.importKey) || taken.has(row.importKey)) {
          result.skipped += 1
          return
        }
        mockState.importKeys.add(row.importKey)
        const planned =
          planning && row.confirmsPlannedId ? storedTransaction(row.confirmsPlannedId) : undefined
        if (
          planned &&
          !planned.deleted &&
          planned.transaction.status === 'PLANNED' &&
          planned.transaction.accountId === account.id
        ) {
          const { transaction } = planned
          transaction.status = 'CONFIRMED'
          transaction.occurredOn = row.occurredOn
          transaction.amount = money(cents, row.amount.currency)
          transaction.importKey = row.importKey
          transaction.memberId = workspace.membershipId
          transaction.version += 1
          result.confirmed += 1
          return
        }
        mockState.transactions.push({
          id: row.id,
          kind: row.kind,
          amount: money(cents, row.amount.currency),
          occurredOn: row.occurredOn,
          accountId: account.id,
          toAccountId: null,
          categoryId: row.categoryId,
          description: row.description ?? null,
          paymentMethod: null,
          tags: [],
          status: 'CONFIRMED',
          scheduledOn: null,
          recurringRuleId: null,
          importKey: row.importKey,
          memberId: workspace.membershipId,
          createdAt: new Date().toISOString(),
          version: 0,
        })
        result.created += 1
      })
      return HttpResponse.json(result)
    },
  ),
]
