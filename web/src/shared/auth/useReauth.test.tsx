import { startAuthentication } from '@simplewebauthn/browser'
import { QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import type { ReactNode } from 'react'
import { IntlProvider } from 'react-intl'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, ApiError, ReauthCancelledError, queryKeys, type MeView } from '@/shared/api'
import { locale, useAppMessages } from '@/shared/i18n'
import { createTestQueryClient } from '@/shared/testing'
import { ToastProvider } from '@/shared/ui'
import { ReauthProvider } from './ReauthProvider'
import { takeReauthUser, takeReturnPath } from './returnPath'
import { useReauth } from './useReauth'

vi.mock('@simplewebauthn/browser')

const reauthRequired = () =>
  new ApiError(403, 'auth.reauthentication-required', 'Confirme que é você para continuar.')

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

beforeEach(() => {
  vi.mocked(startAuthentication).mockResolvedValue({ id: 'credential' } as never)
})

function setup() {
  const queryClient = createTestQueryClient()
  const assign = vi.fn<(url: string) => void>()
  queryClient.setQueryData<MeView>(queryKeys.me, {
    user: { ...mockState.user },
    workspaces: [],
    suggestedWorkspaceName: 'Família Teste',
  })
  function Providers({ children }: { children: ReactNode }) {
    return (
      <IntlProvider locale={locale} messages={useAppMessages()}>
        <QueryClientProvider client={queryClient}>
          <ToastProvider>
            <ReauthProvider assign={assign}>{children}</ReauthProvider>
          </ToastProvider>
        </QueryClientProvider>
      </IntlProvider>
    )
  }
  let reauth!: ReturnType<typeof useReauth>
  function Probe() {
    reauth = useReauth()
    return null
  }
  render(<Probe />, { wrapper: Providers })
  return { queryClient, assign, run: <T,>(action: () => Promise<T>) => reauth(action) }
}

const passkeyButton = () => screen.findByRole('button', { name: 'Usar passkey' })

describe('useReauth', () => {
  it('retriesOnceAfterReauth', async () => {
    const { run } = setup()
    const action = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(reauthRequired())
      .mockResolvedValue('done')

    const result = run(action)

    fireEvent.click(await passkeyButton())
    await expect(result).resolves.toBe('done')
    expect(action).toHaveBeenCalledTimes(2)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Usar passkey' })).not.toBeInTheDocument(),
    )
  })

  it('shows the dialog texts', async () => {
    const { run } = setup()
    void run(() => Promise.reject(reauthRequired())).catch(() => {})

    expect(await screen.findByText('Confirme que é você')).toBeInTheDocument()
    expect(
      screen.getByText('Por segurança, esta ação pede que você entre de novo.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar com Google' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument()
  })

  it('cancelRejects', async () => {
    const { run } = setup()
    const action = vi.fn<() => Promise<string>>().mockRejectedValue(reauthRequired())

    const result = run(action)
    const rejected = expect(result).rejects.toBeInstanceOf(ReauthCancelledError)
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))

    await rejected
    expect(action).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Usar passkey' })).not.toBeInTheDocument()
  })

  it('cancels with the close button too', async () => {
    const { run } = setup()
    const result = run(() => Promise.reject(reauthRequired()))
    const rejected = expect(result).rejects.toBeInstanceOf(ReauthCancelledError)
    await passkeyButton()

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }))

    await rejected
  })

  it('otherErrorsPassThrough', async () => {
    const { run } = setup()
    const error = new ApiError(422, 'validation.failed')

    await expect(run(() => Promise.reject(error))).rejects.toBe(error)
    await expect(run(() => Promise.reject(new ApiError(403, 'role.denied')))).rejects.toMatchObject(
      { code: 'role.denied' },
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('rejects with the error when the retry is refused again, without a loop', async () => {
    const { run } = setup()
    const action = vi.fn<() => Promise<string>>().mockRejectedValue(reauthRequired())

    const result = run(action)
    const rejected = expect(result).rejects.toMatchObject({
      code: 'auth.reauthentication-required',
    })
    fireEvent.click(await passkeyButton())

    await rejected
    expect(action).toHaveBeenCalledTimes(2)
  })

  it('shows reauth.failed, keeps the dialog open and lets the person try again', async () => {
    const { run } = setup()
    vi.mocked(startAuthentication).mockRejectedValueOnce(
      new DOMException('The operation was cancelled.', 'NotAllowedError'),
    )
    const action = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(reauthRequired())
      .mockResolvedValue('done')

    const result = run(action)
    fireEvent.click(await passkeyButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não deu para confirmar. Tente de novo.',
    )
    expect(action).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Usar passkey' }))
    await expect(result).resolves.toBe('done')
  })

  it('shares one dialog between two actions waiting at once', async () => {
    const { run } = setup()
    const first = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(reauthRequired())
      .mockResolvedValue('a')
    const second = vi
      .fn<() => Promise<string>>()
      .mockRejectedValueOnce(reauthRequired())
      .mockResolvedValue('b')

    const results = Promise.all([run(first), run(second)])
    fireEvent.click(await passkeyButton())

    await expect(results).resolves.toEqual(['a', 'b'])
    expect(startAuthentication).toHaveBeenCalledTimes(1)
  })

  it('differentUserClearsSession', async () => {
    const { run, queryClient, assign } = setup()
    server.use(
      http.get(`${API_URL}/v1/me`, () =>
        Response.json({
          user: { ...mockState.user, id: '0192f3a4-0000-7000-8000-0000000000ff' },
          workspaces: [],
          suggestedWorkspaceName: 'Família Teste',
        }),
      ),
    )
    localStorage.setItem('liberdade.workspaceId', 'old')
    const sent: unknown[] = []
    vi.stubGlobal(
      'BroadcastChannel',
      class {
        postMessage(data: unknown) {
          sent.push(data)
        }
        close() {}
      },
    )
    const action = vi.fn<() => Promise<string>>().mockRejectedValue(reauthRequired())

    const result = run(action)
    const rejected = expect(result).rejects.toBeInstanceOf(ReauthCancelledError)
    fireEvent.click(await passkeyButton())

    await rejected
    expect(action).toHaveBeenCalledTimes(1)
    expect(queryClient.getQueryData(queryKeys.me)).toBeUndefined()
    expect(localStorage.getItem('liberdade.workspaceId')).toBeNull()
    expect(assign).toHaveBeenCalledExactlyOnceWith('/inicio')
    expect(sent).toEqual([{ type: 'signed-in', userId: '0192f3a4-0000-7000-8000-0000000000ff' }])
    vi.unstubAllGlobals()
  })

  it('goes to Google with the current page remembered', async () => {
    const { run, assign } = setup()
    window.history.pushState({}, '', '/mais/privacidade')
    void run(() => Promise.reject(reauthRequired())).catch(() => {})

    fireEvent.click(await screen.findByRole('button', { name: 'Entrar com Google' }))

    expect(assign).toHaveBeenCalledExactlyOnceWith(
      'http://localhost:8080/oauth2/authorization/google?reauth=true',
    )
    expect(takeReturnPath()).toBe('/mais/privacidade')
    window.history.pushState({}, '', '/')
  })

  it('records who is signed in before leaving for Google', async () => {
    const { run } = setup()
    void run(() => Promise.reject(reauthRequired())).catch(() => {})

    fireEvent.click(await screen.findByRole('button', { name: 'Entrar com Google' }))

    expect(takeReauthUser()).toBe(mockState.user.id)
  })

  it('locksTheDialogWhileAnAttemptRuns', async () => {
    const { run, assign } = setup()
    void run(() => Promise.reject(reauthRequired())).catch(() => {})
    const google = await screen.findByRole('button', { name: 'Entrar com Google' })

    fireEvent.click(google)
    fireEvent.click(google)

    expect(assign).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Usar passkey' })).toBeDisabled()
  })

  it('throws a clear error without the provider', () => {
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {})
    function Bare() {
      useReauth()
      return null
    }

    expect(() => render(<Bare />)).toThrow('useReauth needs a ReauthProvider above it')
    errors.mockRestore()
  })
})
