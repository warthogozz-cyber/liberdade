import { startRegistration, type RegistrationResponseJSON } from '@simplewebauthn/browser'
import { cleanup, fireEvent, screen } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PasskeyButton } from '@/features/auth/PasskeyButton'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'

vi.mock('@simplewebauthn/browser')

const credential: RegistrationResponseJSON = {
  id: 'credential-id',
  rawId: 'credential-id',
  response: { clientDataJSON: 'client-data', attestationObject: 'attestation' },
  clientExtensionResults: {},
  type: 'public-key',
}

beforeEach(() => {
  seedMockFamily()
  vi.mocked(startRegistration).mockResolvedValue(credential)
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  vi.restoreAllMocks()
})

function renderButton() {
  return renderWithProviders(<PasskeyButton />, { path: '/mais' })
}

const createButton = () => screen.findByRole('button', { name: 'Criar passkey neste aparelho' })

describe('PasskeyButton', () => {
  it('passkeyExplained', async () => {
    renderButton()
    await createButton()

    expect(
      screen.getAllByText(
        'Passkey é entrar com a digital, o rosto ou o PIN do aparelho, sem senha.',
      ),
    ).toHaveLength(1)
  })

  it('creates a passkey labelled for this device', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/webauthn/register`, async ({ request }) => {
        bodies.push(await request.clone().json())
      }),
    )
    const { queryClient } = renderButton()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    fireEvent.click(await createButton())

    expect(await screen.findByText('Passkey criada.')).toHaveAttribute('role', 'status')
    // The date of the new passkey comes from a fresh `/v1/me`.
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['me'] })
    expect(bodies).toEqual([{ publicKey: { credential, label: 'Este aparelho' } }])
    expect(startRegistration).toHaveBeenCalledWith({
      optionsJSON: expect.objectContaining({
        rp: { id: 'localhost', name: 'Liberdade' },
      }) as unknown,
    })
  })

  it('shows when the last passkey was created, in the family’s time zone', async () => {
    // Already 3 November in UTC, still 2 November in Fortaleza (UTC-3).
    mockState.user.passkeyCreatedAt = '2026-11-03T01:00:00Z'
    renderButton()

    expect(
      await screen.findByText('Última passkey criada em 2 de novembro de 2026'),
    ).toBeInTheDocument()
  })

  it('says the passkey was not created when the device refuses', async () => {
    vi.mocked(startRegistration).mockRejectedValue(new Error('refused'))
    renderButton()

    fireEvent.click(await createButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A passkey não foi criada. Tente de novo.',
    )
    expect(await createButton()).toBeEnabled()
  })

  it('is disabled offline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderButton()

    expect(await createButton()).toBeDisabled()
    expect(screen.getByText('Criar passkey precisa de internet.')).toBeInTheDocument()
  })
})
