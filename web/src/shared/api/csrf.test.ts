import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import {
  API_URL,
  ApiError,
  createWorkspace,
  getMe,
  postJson,
  postTransaction,
  type CreateTransactionRequest,
} from '@/shared/api'

const CSRF_INVALID = 'Sua sessão foi atualizada. Tente de novo.'

let requests: Request[] = []

function record({ request }: { request: Request }) {
  requests.push(request)
}

function calls(method: string, path: string) {
  return requests.filter(
    (request) => request.method === method && new URL(request.url).pathname === path,
  )
}

beforeEach(() => {
  requests = []
  server.events.on('request:start', record)
})

afterEach(() => {
  server.events.removeListener('request:start', record)
})

describe('csrfFetch', () => {
  it('sends the token in X-XSRF-TOKEN on a POST', async () => {
    await createWorkspace('Família Teste')

    const [post] = calls('POST', '/v1/workspaces')
    expect(post.headers.get('X-XSRF-TOKEN')).toBe('mock-csrf')
  })

  it('fetches a new token and resends once after a 403 csrf.invalid', async () => {
    server.use(
      http.post(`${API_URL}/v1/workspaces`, () => problem(403, 'csrf.invalid', CSRF_INVALID), {
        once: true,
      }),
    )

    await expect(createWorkspace('Família Teste')).resolves.toMatchObject({
      name: 'Família Teste',
      role: 'OWNER',
    })
    expect(calls('GET', '/v1/csrf')).toHaveLength(2)
    expect(calls('POST', '/v1/workspaces')).toHaveLength(2)
  })

  it('gives up after a second 403 csrf.invalid', async () => {
    server.use(
      http.post(`${API_URL}/v1/workspaces`, () => problem(403, 'csrf.invalid', CSRF_INVALID)),
    )

    const error = await createWorkspace('Família Teste').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 403, code: 'csrf.invalid' })
    expect(calls('POST', '/v1/workspaces')).toHaveLength(2)
  })

  it('does not resend after any other 403', async () => {
    server.use(
      http.post(`${API_URL}/v1/workspaces`, () =>
        problem(403, 'auth.forbidden', 'Você não tem permissão para isso.'),
      ),
    )

    await expect(createWorkspace('Família Teste')).rejects.toMatchObject({
      status: 403,
      code: 'auth.forbidden',
    })
    expect(calls('POST', '/v1/workspaces')).toHaveLength(1)
  })

  it('leaves GET requests without a token', async () => {
    await getMe()

    const [me] = calls('GET', '/v1/me')
    expect(me.headers.has('X-XSRF-TOKEN')).toBe(false)
    expect(calls('GET', '/v1/csrf')).toHaveLength(0)
  })

  it('shares one token request between POSTs sent at the same time', async () => {
    await Promise.all([createWorkspace('Família Teste'), postJson('/logout')])

    expect(calls('GET', '/v1/csrf')).toHaveLength(1)
    expect(calls('POST', '/v1/workspaces')[0].headers.get('X-XSRF-TOKEN')).toBe('mock-csrf')
    expect(calls('POST', '/logout')[0].headers.get('X-XSRF-TOKEN')).toBe('mock-csrf')
  })

  it('asks for the token again after the token request failed', async () => {
    server.use(
      http.get(
        `${API_URL}/v1/csrf`,
        () =>
          problem(500, 'internal', 'Algo deu errado do nosso lado. Tente de novo em instantes.'),
        { once: true },
      ),
    )

    await expect(createWorkspace('Família Teste')).rejects.toMatchObject({
      status: 500,
      code: 'internal',
    })
    await expect(createWorkspace('Família Teste')).resolves.toMatchObject({ role: 'OWNER' })
    expect(calls('GET', '/v1/csrf')).toHaveLength(2)
    expect(calls('POST', '/v1/workspaces')).toHaveLength(1)
  })

  it('sends the transaction id as the Idempotency-Key', async () => {
    const workspace = seedMockFamily()
    const payload: CreateTransactionRequest = {
      id: '0192f3a4-5b6c-7d8e-9f01-23456789abcd',
      kind: 'EXPENSE',
      amount: { amount: '12.50', currency: 'BRL' },
      occurredOn: '2026-11-03',
      accountId: mockState.accounts[0].id,
      categoryId: mockState.categories[0].id,
      description: 'Feira',
    }

    await expect(postTransaction(workspace.id, payload)).resolves.toMatchObject({
      id: payload.id,
    })

    const [post] = calls('POST', `/v1/workspaces/${workspace.id}/transactions`)
    expect(post.headers.get('Idempotency-Key')).toBe(payload.id)
  })
})
