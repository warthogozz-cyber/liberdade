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
import { createFamily, type MockAccount, mockState } from './data'

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

/** A problem answer, shaped like the API's (`application/problem+json`). */
export function problem(status: number, code: string, detail?: string, errors?: FieldError[]) {
  return HttpResponse.json(
    { status, detail, code, ...(errors && { errors }) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

const malformed = () => problem(400, 'request.malformed', 'Não conseguimos ler os dados enviados.')
const invalid = (errors: FieldError[]) =>
  problem(422, 'request.invalid', 'Alguns campos precisam de ajuste.', errors)
const rejected = (code: string, detail: string) => problem(422, code, detail)

const url = (path: string) => `${API_URL}${path}`

/** The API answers 403 `csrf.invalid` to any write without the token. */
function withoutToken(request: Request) {
  return request.headers.get(CSRF.headerName) !== CSRF.token
}

/** The signed-in user, then their family `ws`, guard every route under a family. */
function guard(ws: string): MeWorkspace | Response {
  if (!mockState.signedIn) return problem(401, 'auth.required', 'Entre para continuar.')
  return (
    mockState.workspaces.find((workspace) => workspace.id === ws) ??
    problem(404, 'workspace.not-found', 'Família não encontrada.')
  )
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

function signedCents({ kind, amount }: TransactionView): bigint {
  const cents = parseCents(amount.amount)
  return kind === 'INCOME' ? cents : kind === 'EXPENSE' ? -cents : 0n
}

function movementCents(accountId: string): bigint {
  return mockState.transactions
    .filter((transaction) => transaction.accountId === accountId)
    .reduce((sum, transaction) => sum + signedCents(transaction), 0n)
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

/** What the API cannot even read is a 400, not a 422: a bad UUID, date, kind or number. */
function readable(body: CreateTransactionRequest) {
  const isUuid = (value: unknown) =>
    value == null || (typeof value === 'string' && UUID.test(value))
  return (
    [body.id, body.accountId, body.categoryId].every(isUuid) &&
    (body.kind == null || KINDS.includes(body.kind)) &&
    (body.occurredOn == null || DATE.test(body.occurredOn)) &&
    (body.amount?.amount == null || NUMBER.test(String(body.amount.amount)))
  )
}

const REQUIRED = ['id', 'kind', 'amount', 'occurredOn', 'accountId', 'categoryId'] as const

/** The body checks of `CreateTransactionRequest`; the description comes back stripped. */
function validateTransaction(body: CreateTransactionRequest) {
  const errors: FieldError[] = REQUIRED.filter((field) => body[field] == null).map((field) => ({
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
    stored.categoryId === request.categoryId &&
    stored.description === request.description
  )
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
      workspaces: mockState.workspaces,
      suggestedWorkspaceName: mockState.suggestedWorkspaceName,
    })
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
      mockState.transactions.filter(
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
    const month = new URL(request.url).searchParams.get('month') ?? ''
    if (!MONTH.test(month)) return malformed()
    return HttpResponse.json(
      mockState.transactions
        .filter((transaction) => transaction.occurredOn.startsWith(`${month}-`))
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
    const { errors, cents, description } = validateTransaction(body)
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
      categoryId: body.categoryId,
      description,
      memberId: workspace.membershipId,
      createdAt: new Date().toISOString(),
    }
    const stored = mockState.transactions.find((existing) => existing.id === body.id)
    if (stored) {
      return sameContent(stored, transaction)
        ? HttpResponse.json(stored, { status: 200 })
        : problem(409, 'transaction.id-conflict', 'Este lançamento já existe com outros dados.')
    }
    if (body.kind === 'TRANSFER') {
      return rejected('transaction.kind-unsupported', 'Transferências ainda não estão disponíveis.')
    }
    const account = mockState.accounts.find(
      (existing) => existing.id === body.accountId && existing.archivedAt === null,
    )
    if (!account) {
      return rejected('transaction.account-not-found', 'Conta não encontrada nesta família.')
    }
    const category = mockState.categories.find((existing) => existing.id === body.categoryId)
    if (!category) {
      return rejected('transaction.category-not-found', 'Categoria não encontrada nesta família.')
    }
    if (category.kind !== body.kind) {
      return rejected(
        'transaction.category-kind-mismatch',
        'Esta categoria não combina com o tipo do lançamento.',
      )
    }
    if (body.amount.currency !== account.currency) {
      return rejected('transaction.currency-mismatch', 'A moeda do valor não é a mesma da conta.')
    }
    mockState.transactions.push(transaction)
    return HttpResponse.json(transaction, { status: 201 })
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/dashboard'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const month = new URL(request.url).searchParams.get('month') ?? ''
    if (!MONTH.test(month)) return malformed()
    const expenses = mockState.transactions
      .filter(
        (transaction) =>
          transaction.kind === 'EXPENSE' && transaction.occurredOn.startsWith(`${month}-`),
      )
      .reduce((sum, transaction) => sum + parseCents(transaction.amount.amount), 0n)
    const balance = mockState.accounts
      .filter((account) => account.archivedAt === null)
      .reduce((sum, account) => sum + balanceCents(account), 0n)
    return HttpResponse.json({
      month,
      expenses: money(expenses, workspace.currency),
      balance: money(balance, workspace.currency),
    })
  }),
]
