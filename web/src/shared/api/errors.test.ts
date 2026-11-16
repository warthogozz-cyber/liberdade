import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, ApiError, getMe, postJson } from '@/shared/api'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ApiError', () => {
  it('carries the status, code and detail of a problem body', async () => {
    server.use(
      http.get(`${API_URL}/v1/me`, () =>
        problem(422, 'request.invalid', 'Alguns campos precisam de ajuste.'),
      ),
    )

    const error = await getMe().catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 422,
      code: 'request.invalid',
      detail: 'Alguns campos precisam de ajuste.',
      message: 'Alguns campos precisam de ajuste.',
    })
  })

  it('keepsExtraProperties', async () => {
    const current = { id: 'a', version: 3, amount: { amount: '90.00', currency: 'BRL' } }
    server.use(
      http.get(`${API_URL}/v1/me`, () =>
        problem(409, 'transaction.version-conflict', 'Mudou.', undefined, { current }),
      ),
    )

    const error = (await getMe().catch((caught: unknown) => caught)) as ApiError

    expect(error.extra.current).toEqual(current)
    expect(error.extra).not.toHaveProperty('code')
    expect(error.extra).not.toHaveProperty('detail')
    expect(error.extra).not.toHaveProperty('status')
    expect(error.detail).toBe('Mudou.')
  })

  it('has no extra properties when the problem has none', async () => {
    server.use(http.get(`${API_URL}/v1/me`, () => problem(422, 'request.invalid', 'Ajuste.')))

    const error = (await getMe().catch((caught: unknown) => caught)) as ApiError

    expect(error.extra).toEqual({})
  })

  it('turns a fetch rejected with a TypeError into a network error', async () => {
    server.use(http.get(`${API_URL}/v1/me`, () => HttpResponse.error()))

    const error = await getMe().catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 0, code: 'network' })
  })

  it('lets any other fetch failure through unchanged', async () => {
    const aborted = new DOMException('The operation was aborted.', 'AbortError')
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(aborted)

    await expect(getMe()).rejects.toBe(aborted)
  })

  it('reports a response without a problem body as an http error', async () => {
    server.use(
      http.get(`${API_URL}/v1/me`, () =>
        HttpResponse.html('<h1>Bad Gateway</h1>', { status: 502 }),
      ),
    )

    const error = await getMe().catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 502, code: 'http', message: 'http' })
    expect((error as ApiError).detail).toBeUndefined()
  })

  it('reports a JSON body without a code as an http error', async () => {
    server.use(
      http.get(`${API_URL}/v1/me`, () => HttpResponse.json({ message: 'nope' }, { status: 500 })),
    )

    await expect(getMe()).rejects.toMatchObject({ status: 500, code: 'http' })
  })
})

describe('postJson', () => {
  it('resolves to undefined on a 204', async () => {
    await expect(postJson('/logout')).resolves.toBeUndefined()
  })

  it('sends the body as JSON and resolves to the parsed answer', async () => {
    let received: unknown
    server.use(
      http.post(`${API_URL}/webauthn/echo`, async ({ request }) => {
        received = { type: request.headers.get('Content-Type'), body: await request.json() }
        return HttpResponse.json({ ok: true })
      }),
    )

    await expect(postJson('/webauthn/echo', { a: 1 })).resolves.toEqual({ ok: true })
    expect(received).toEqual({ type: 'application/json', body: { a: 1 } })
  })

  it('throws the ApiError of a failed call', async () => {
    server.use(
      http.post(`${API_URL}/logout`, () => problem(401, 'auth.required', 'Entre para continuar.')),
    )

    await expect(postJson('/logout')).rejects.toMatchObject({
      status: 401,
      code: 'auth.required',
      detail: 'Entre para continuar.',
    })
  })

  it('throws an http error for a failure without a problem body', async () => {
    server.use(
      http.post(`${API_URL}/logout`, () =>
        HttpResponse.text('Service Unavailable', { status: 503 }),
      ),
    )

    await expect(postJson('/logout')).rejects.toMatchObject({ status: 503, code: 'http' })
  })
})
