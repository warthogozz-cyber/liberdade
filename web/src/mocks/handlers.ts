import { http, HttpResponse } from 'msw'
import {
  API_URL,
  type ApiMoney,
  type CreateTransactionRequest,
  type MeWorkspace,
  type TransactionView,
} from '@/shared/api'
import { centsToApi, MAX_CENTS, parseCents } from '@/shared/money'
import { createFamily, mockState } from './data'

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

function balanceCents(accountId: string): bigint {
  return mockState.transactions
    .filter((transaction) => transaction.accountId === accountId)
    .reduce((sum, transaction) => sum + signedCents(transaction), 0n)
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

/** The routes of Tasks 3, 5 and 6, with the API's error codes, plus `POST /logout`. */
export const handlers = [
  http.get(url('/v1/csrf'), () => HttpResponse.json(CSRF)),

  http.post(url('/logout'), ({ request }) => {
    if (withoutToken(request)) {
      return problem(403, 'csrf.invalid', 'Sua sessão foi atualizada. Tente de novo.')
    }
    return new HttpResponse(null, { status: 204 })
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
    return HttpResponse.json(
      mockState.accounts.map((account) => ({
        ...account,
        balance: money(balanceCents(account.id), account.currency),
      })),
    )
  }),

  http.get<{ ws: string }>(url('/v1/workspaces/:ws/categories'), ({ request, params }) => {
    const workspace = guard(params.ws)
    if (workspace instanceof Response) return workspace
    const kind = new URL(request.url).searchParams.get('kind')
    if (kind !== 'EXPENSE' && kind !== 'INCOME') return malformed()
    // Most used in the last 90 days first; ties keep the seed order (the sort is stable).
    const since = new Date(Date.now() - 89 * DAY).toISOString().slice(0, 10)
    const uses = (categoryId: string) =>
      mockState.transactions.filter(
        (transaction) => transaction.categoryId === categoryId && transaction.occurredOn >= since,
      ).length
    return HttpResponse.json(
      mockState.categories
        .filter((category) => category.kind === kind)
        .sort((a, b) => uses(b.id) - uses(a.id)),
    )
  }),

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
    const key = request.headers.get('Idempotency-Key')
    const body = (await readJson(request)) as CreateTransactionRequest | undefined
    if (key === null || typeof body !== 'object' || body === null || !readable(body)) {
      return malformed()
    }
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
    const account = mockState.accounts.find((existing) => existing.id === body.accountId)
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
    const balance = mockState.accounts.reduce((sum, account) => sum + balanceCents(account.id), 0n)
    return HttpResponse.json({
      month,
      expenses: money(expenses, workspace.currency),
      balance: money(balance, workspace.currency),
    })
  }),
]
