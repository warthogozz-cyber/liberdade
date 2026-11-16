import {
  browserSupportsWebAuthn,
  startAuthentication,
  type AuthenticationResponseJSON,
} from '@simplewebauthn/browser'
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SignInPage } from '@/features/auth/SignInPage'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, postJson } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { startGoogleSignIn } from './google'

vi.mock('./google')
vi.mock('@simplewebauthn/browser')

const credential: AuthenticationResponseJSON = {
  id: 'credential-id',
  rawId: 'credential-id',
  response: { clientDataJSON: 'client-data', authenticatorData: 'authenticator', signature: 'sig' },
  clientExtensionResults: {},
  type: 'public-key',
}

beforeEach(() => {
  vi.mocked(browserSupportsWebAuthn).mockReturnValue(true)
  vi.mocked(startAuthentication).mockResolvedValue(credential)
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  vi.restoreAllMocks()
})

function renderSignIn(route = '/entrar') {
  return renderWithProviders(<SignInPage />, { path: '/entrar', route })
}

const passkeyButton = () => screen.getByRole('button', { name: 'Entrar com passkey' })

describe('SignInPage', () => {
  it('shows the app name, the tagline and the no-password note', () => {
    renderSignIn()

    expect(screen.getByRole('heading', { level: 1, name: 'Liberdade' })).toBeInTheDocument()
    expect(screen.getByText('As finanças da sua família, simples e em paz.')).toBeInTheDocument()
    expect(screen.getByText('Sem senha. Nunca pedimos a senha do seu banco.')).toBeInTheDocument()
    expect(document.title).toBe('Entrar · Liberdade')
  })

  it('shows the failed sign-in from ?erro=login', () => {
    renderSignIn('/entrar?erro=login')

    expect(screen.getByRole('alert')).toHaveTextContent('Não deu para entrar. Tente de novo.')
  })

  it('shows the rate limit from ?erro=limite', () => {
    renderSignIn('/entrar?erro=limite')

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Muitas tentativas. Espere um minuto e tente de novo.',
    )
  })

  it('starts the Google sign-in once', () => {
    const { queryClient } = renderSignIn()

    fireEvent.click(screen.getByRole('button', { name: 'Entrar com Google' }))

    expect(startGoogleSignIn).toHaveBeenCalledTimes(1)
    expect(startGoogleSignIn).toHaveBeenCalledWith(queryClient)
  })

  it('frees the buttons when Back restores the page from the bfcache (M1)', () => {
    vi.mocked(startGoogleSignIn).mockReturnValue(new Promise(() => {}))
    renderSignIn()
    const google = screen.getByRole('button', { name: 'Entrar com Google' })
    fireEvent.click(google)
    expect(google).toBeDisabled()

    // A page loaded afresh says persisted: false, and nothing changes.
    fireEvent(window, Object.assign(new Event('pageshow'), { persisted: false }))
    expect(google).toBeDisabled()
    fireEvent(window, Object.assign(new Event('pageshow'), { persisted: true }))

    expect(google).toBeEnabled()
    expect(passkeyButton()).toBeEnabled()
  })

  it('signs in with a passkey and goes to /', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/login/webauthn`, async ({ request }) => {
        bodies.push(await request.clone().json())
      }),
    )
    const { router } = renderSignIn()

    fireEvent.click(passkeyButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(bodies).toEqual([credential])
    expect(startAuthentication).toHaveBeenCalledWith({
      optionsJSON: expect.objectContaining({ rpId: 'localhost' }) as unknown,
    })
  })

  it('forgets the previous cache and CSRF token after a passkey sign-in (T10-csrf)', async () => {
    const tokenRequests: string[] = []
    const record = ({ request }: { request: Request }) => {
      if (new URL(request.url).pathname === '/v1/csrf') tokenRequests.push(request.method)
    }
    server.events.on('request:start', record)
    try {
      const { router, queryClient } = renderSignIn()
      queryClient.setQueryData(['transactions', 'old'], ['from the previous person'])

      fireEvent.click(passkeyButton())

      await waitFor(() => expect(router.state.location.pathname).toBe('/'))
      expect(queryClient.getQueryCache().getAll()).toEqual([])
      expect(tokenRequests).toEqual(['GET'])
      await postJson('/logout')
      expect(tokenRequests).toEqual(['GET', 'GET'])
    } finally {
      server.events.removeListener('request:start', record)
    }
  })

  it('starts one passkey sign-in for two quick taps', async () => {
    const { router } = renderSignIn()

    fireEvent.click(passkeyButton())
    fireEvent.click(passkeyButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(startAuthentication).toHaveBeenCalledTimes(1)
  })

  it('shows nothing when the person cancels the passkey', async () => {
    vi.mocked(startAuthentication).mockRejectedValue(
      new DOMException('The operation was cancelled.', 'NotAllowedError'),
    )
    renderSignIn()

    fireEvent.click(passkeyButton())

    expect(passkeyButton()).toBeDisabled()
    await waitFor(() => expect(passkeyButton()).toBeEnabled())
    expect(startAuthentication).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the rate limit when the passkey sign-in answers 429', async () => {
    server.use(
      http.post(`${API_URL}/login/webauthn`, () =>
        problem(429, 'rate.limited', 'Muitas tentativas. Espere um minuto e tente de novo.'),
      ),
    )
    renderSignIn()

    fireEvent.click(passkeyButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Muitas tentativas. Espere um minuto e tente de novo.',
    )
    expect(passkeyButton()).toBeEnabled()
  })

  it('shows the passkey error for any other failure', async () => {
    vi.mocked(startAuthentication).mockRejectedValue(new Error('broken'))
    renderSignIn()

    fireEvent.click(passkeyButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não deu para entrar com a passkey. Tente de novo ou entre com Google.',
    )
  })

  it('disables both buttons offline', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderSignIn()

    expect(screen.getByRole('button', { name: 'Entrar com Google' })).toBeDisabled()
    expect(passkeyButton()).toBeDisabled()
    expect(screen.getByText('Sem conexão. Entrar precisa de internet.')).toBeInTheDocument()
  })

  it('hides the passkey button where the browser has no WebAuthn', () => {
    vi.mocked(browserSupportsWebAuthn).mockReturnValue(false)
    renderSignIn()

    expect(screen.queryByRole('button', { name: 'Entrar com passkey' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar com Google' })).toBeEnabled()
  })
})
