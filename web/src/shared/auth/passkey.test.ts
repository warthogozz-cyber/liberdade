import { startAuthentication, startRegistration } from '@simplewebauthn/browser'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { registerPasskey, signInWithPasskey } from './passkey'

vi.mock('@simplewebauthn/browser')

afterEach(() => vi.resetAllMocks())

describe('passkey', () => {
  it('signs in with the credential the device gives', async () => {
    const bodies: unknown[] = []
    vi.mocked(startAuthentication).mockResolvedValue({ id: 'credential' } as never)
    server.use(
      http.post(`${API_URL}/login/webauthn`, async ({ request }) => {
        bodies.push(await request.clone().json())
        return Response.json({ authenticated: true })
      }),
    )

    await signInWithPasskey()

    expect(bodies).toEqual([{ id: 'credential' }])
  })

  it('registers a passkey with its label', async () => {
    const bodies: unknown[] = []
    vi.mocked(startRegistration).mockResolvedValue({ id: 'new' } as never)
    server.use(
      http.post(`${API_URL}/webauthn/register`, async ({ request }) => {
        bodies.push(await request.clone().json())
        return Response.json({ success: true })
      }),
    )

    await registerPasskey('Este aparelho')

    expect(bodies).toEqual([{ publicKey: { credential: { id: 'new' }, label: 'Este aparelho' } }])
  })
})
