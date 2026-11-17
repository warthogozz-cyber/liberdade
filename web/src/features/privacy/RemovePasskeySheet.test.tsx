import { startAuthentication } from '@simplewebauthn/browser'
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { PasskeyList } from './PasskeyList'

vi.mock('@simplewebauthn/browser')

beforeEach(() => {
  vi.mocked(startAuthentication).mockResolvedValue({ id: 'credential' } as never)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

async function askToRemove(name: string) {
  fireEvent.click(await screen.findByRole('button', { name: `Remover passkey ${name}` }))
  await screen.findByRole('dialog')
  expect(screen.getByText(`Remover a passkey ${name}?`)).toBeInTheDocument()
}

describe('RemovePasskeySheet', () => {
  it('removesAfterReauth', async () => {
    seedMockFamily()
    let calls = 0
    server.use(
      http.delete(`${API_URL}/v1/me/passkeys/:id`, () => {
        calls += 1
        if (calls === 1) {
          return problem(403, 'auth.reauthentication-required', 'Confirme que é você.')
        }
        // No answer: the default handler takes the request next.
      }),
    )
    renderWithProviders(<PasskeyList />)

    await askToRemove('Celular')
    fireEvent.click(screen.getByRole('button', { name: 'Remover' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    await waitFor(() => expect(calls).toBe(2))
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Remover passkey Celular' }),
      ).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: 'Remover passkey Notebook' })).toBeInTheDocument()
    expect(mockState.activity[0]?.action).toBe('passkey.removed')
  })

  it('cancelledReauthKeepsPasskey', async () => {
    seedMockFamily()
    let calls = 0
    server.use(
      http.delete(`${API_URL}/v1/me/passkeys/:id`, () => {
        calls += 1
        return problem(403, 'auth.reauthentication-required', 'Confirme que é você.')
      }),
    )
    renderWithProviders(<PasskeyList />)

    await askToRemove('Celular')
    fireEvent.click(screen.getByRole('button', { name: 'Remover' }))
    await screen.findByRole('button', { name: 'Usar passkey' })
    // While the dialog is open, a second tap must not start another attempt.
    expect(
      screen
        .getAllByRole('button', { name: 'Remover', hidden: true })
        .every((b) => b.hasAttribute('disabled')),
    ).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Usar passkey' })).not.toBeInTheDocument(),
    )
    expect(calls).toBe(1)
    expect(mockState.passkeys.some((passkey) => passkey.label === 'Celular')).toBe(true)
    expect(await screen.findByRole('button', { name: 'Remover' })).toBeEnabled()
  })

  it('lastMethodMessage', async () => {
    seedMockFamily()
    mockState.hasGoogle = false
    mockState.passkeys = mockState.passkeys.slice(0, 1)
    server.use(
      http.delete(`${API_URL}/v1/me/passkeys/:id`, () =>
        HttpResponse.json(
          { type: 'about:blank', status: 422, code: 'passkey.last-method', detail: 'Do servidor.' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderWithProviders(<PasskeyList />)

    await askToRemove('Notebook')
    fireEvent.click(screen.getByRole('button', { name: 'Remover' }))

    expect(
      await screen.findByText(
        'Esta é a única forma de entrar na sua conta. Crie outra passkey antes de remover esta.',
      ),
    ).toBeInTheDocument()
    expect(mockState.passkeys).toHaveLength(1)
    expect(startAuthentication).not.toHaveBeenCalled()
  })
})
