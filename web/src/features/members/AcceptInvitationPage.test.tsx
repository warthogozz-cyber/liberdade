import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import type { PersistedClient } from '@tanstack/react-query-persist-client'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedMockInvitation } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, type MeView } from '@/shared/api'
import { readSelectedWorkspaceId } from '@/shared/api/workspaceChoice'
import { registerPasskey } from '@/shared/auth/passkey'
import { broadcastSignedIn, db } from '@/shared/offline'
import { renderWithProviders } from '@/shared/testing'
import { AcceptInvitationPage } from './AcceptInvitationPage'

const assign = vi.hoisted(() => vi.fn<(url: string) => void>())

vi.mock('@/shared/offline', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/offline')>()
  return { ...actual, broadcastSignedIn: vi.fn() }
})

vi.mock('@/shared/auth/passkey', () => ({ registerPasskey: vi.fn() }))
// The real function, saving the return path as it does; only the page change is replaced.
vi.mock('@/shared/auth/google', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/auth/google')>()
  return {
    ...actual,
    startGoogleSignIn: (options?: Parameters<typeof actual.startGoogleSignIn>[0]) =>
      actual.startGoogleSignIn(options, assign),
  }
})

const TOKEN = 'C'.repeat(43)
const TOKEN_KEY = 'liberdade.invitationToken'
const PENDING_KEY = 'liberdade.pendingPasskey'
const ACCEPT = `${API_URL}/v1/invitations/accept`
const ME = `${API_URL}/v1/me`

beforeEach(() => {
  sessionStorage.setItem(TOKEN_KEY, TOKEN)
  vi.mocked(registerPasskey).mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.clearAllMocks()
  sessionStorage.clear()
  localStorage.clear()
})

function renderAccept() {
  return renderWithProviders(null, {
    route: '/convite',
    routes: [
      { path: '/convite', element: <AcceptInvitationPage /> },
      { path: '/inicio', element: <p>Início da família</p> },
      { path: '*', element: null },
    ],
  })
}

describe('AcceptInvitationPage', () => {
  it('signedInJoins', async () => {
    seedMockFamily()
    seedMockInvitation({ token: TOKEN })
    renderAccept()

    expect(
      await screen.findByText(/Bia Teste convidou você para a Família Bia Teste como Adulto/),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Entrar na família' }))

    expect(await screen.findByText('Início da família')).toBeInTheDocument()
    expect(readSelectedWorkspaceId()).toBe('ws-b')
    expect(mockState.workspaces.some((workspace) => workspace.id === 'ws-b')).toBe(true)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('joinedFamilyIsSavedForAReload', async () => {
    seedMockFamily()
    seedMockInvitation({ token: TOKEN })
    renderAccept()

    fireEvent.click(await screen.findByRole('button', { name: 'Entrar na família' }))

    expect(await screen.findByText('Início da família')).toBeInTheDocument()
    // A reload right away decides on the saved `me`: it must already hold the new family.
    const saved = await db.kv.get('liberdade-query-cache')
    const me = saved
      ? ((JSON.parse(saved.value) as PersistedClient).clientState.queries.find(
          (query) => query.queryHash === '["me"]',
        )?.state.data as MeView | undefined)
      : undefined
    expect(me?.workspaces.map((workspace) => workspace.id)).toContain('ws-b')
  })

  it('anonymousPasskeyPath', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Ana Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))

    // Joined as a new person, and now the passkey is not optional.
    expect(
      await screen.findByRole('heading', { name: 'Crie sua passkey para voltar depois' }),
    ).toBeInTheDocument()
    expect(mockState.user.displayName).toBe('Ana Teste')
    expect(screen.queryByRole('link', { name: 'Ir para o início' })).not.toBeInTheDocument()
    // A new session: the other tabs hear of it, and the family it joined is the chosen one.
    expect(broadcastSignedIn).toHaveBeenCalledWith(mockState.user.id)
    expect(readSelectedWorkspaceId()).toBe('ws-b')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey' }))

    expect(await screen.findByText('Início da família')).toBeInTheDocument()
    expect(registerPasskey).toHaveBeenCalledWith('Convidada')
  })

  it('retriesAPasskeyThatFailed', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    vi.mocked(registerPasskey).mockRejectedValueOnce(new Error('closed'))
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Ana Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Criar passkey' }))

    expect(await screen.findByText('A passkey não foi criada. Tente de novo.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByText('Início da família')).toBeInTheDocument()
  })

  it('youthHasNoGoogle', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN, role: 'YOUTH' })
    renderAccept()

    expect(
      await screen.findByRole('button', { name: 'Criar passkey neste aparelho' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Entrar com Google' })).not.toBeInTheDocument()
    expect(screen.getByText('Para o Jovem, a entrada é só por passkey.')).toBeInTheDocument()
  })

  it('signedInYouthMustSignOutFirst', async () => {
    seedMockFamily()
    seedMockInvitation({ token: TOKEN, role: 'YOUTH' })
    const logouts: string[] = []
    server.use(
      http.post(`${API_URL}/logout`, ({ request }) => {
        logouts.push(request.method)
      }),
    )
    const { router } = renderAccept()

    expect(
      await screen.findByText(
        'Convites de Jovem só podem ser aceitos com uma passkey nova. Saia da sua conta para continuar.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Entrar na família' })).not.toBeInTheDocument()
    const signOut = screen.getByRole('button', { name: 'Sair' })
    await waitFor(() => expect(signOut).toBeEnabled())
    fireEvent.click(signOut)

    // Signed out, the same link now asks for a new passkey-only account.
    expect(await screen.findByLabelText('Como você se chama?')).toBeInTheDocument()
    expect(logouts).toEqual(['POST'])
    expect(router.state.location.pathname).toBe('/convite')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe(TOKEN)
    expect(screen.queryByRole('button', { name: 'Entrar com Google' })).not.toBeInTheDocument()
  })

  it('youthNeedsPasskeyAtAcceptKeepsTheToken', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN, role: 'YOUTH' })
    server.use(
      http.post(ACCEPT, () =>
        problem(422, 'invitation.youth-needs-passkey', 'Detalhe do servidor.'),
      ),
    )
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Leo Teste' },
    })
    // The page did not see the session, but the API did.
    mockState.signedIn = true
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))

    expect(
      await screen.findByText(
        'Convites de Jovem só podem ser aceitos com uma passkey nova. Saia da sua conta para continuar.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByText('Detalhe do servidor.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe(TOKEN)
  })

  it('goneMessage', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN, expiresInMs: -1000 })
    renderAccept()

    expect(
      await screen.findByText('Este convite não vale mais. Peça um novo para quem convidou.'),
    ).toBeInTheDocument()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('googleSavesReturn', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    renderAccept()

    fireEvent.click(await screen.findByRole('button', { name: 'Entrar com Google' }))

    await waitFor(() => expect(assign).toHaveBeenCalled())
    expect(assign.mock.calls[0]![0]).toContain('/oauth2/authorization/google')
    // The way back lands on /convite, which finds the token where the page left it.
    expect(sessionStorage.getItem('liberdade.returnPath')).toBe('/convite')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe(TOKEN)
  })

  it('goneAtAcceptClearsTheToken', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    server.use(http.post(ACCEPT, () => problem(410, 'invitation.gone', 'Detalhe do servidor.')))
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Ana Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))

    expect(
      await screen.findByText('Este convite não vale mais. Peça um novo para quem convidou.'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Detalhe do servidor.')).not.toBeInTheDocument()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('notFoundAtAcceptClearsTheToken', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    server.use(
      http.post(ACCEPT, () => problem(404, 'invitation.not-found', 'Detalhe do servidor.')),
    )
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Ana Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))

    expect(await screen.findByText('Convite não encontrado.')).toBeInTheDocument()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('otherAcceptFailureClearsTheTokenAndOffersHome', async () => {
    seedMockFamily()
    seedMockInvitation({ token: TOKEN })
    server.use(
      http.post(ACCEPT, () => problem(409, 'invitation.already-member', 'Detalhe do servidor.')),
    )
    renderAccept()

    fireEvent.click(await screen.findByRole('button', { name: 'Entrar na família' }))

    expect(
      await screen.findByText('Não deu para entrar na família agora. Tente de novo.'),
    ).toBeInTheDocument()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    // The link is gone, so trying again would only fail: the way out is the start.
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir para o início' })).toBeInTheDocument()
  })

  it('retryAfterAcceptOnlyRefetchesMe', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    let accepts = 0
    let meReads = 0
    server.use(
      http.post(ACCEPT, () => {
        accepts += 1
        return undefined
      }),
      http.get(ME, () => {
        meReads += 1
        // The first read is the page looking for a session; the second is right after accept.
        return meReads === 2 ? problem(500, 'internal.error', 'Falhou.') : undefined
      }),
    )
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Ana Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))

    fireEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))

    expect(
      await screen.findByRole('heading', { name: 'Crie sua passkey para voltar depois' }),
    ).toBeInTheDocument()
    expect(accepts).toBe(1)
    expect(meReads).toBe(3)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(broadcastSignedIn).toHaveBeenCalledWith(mockState.user.id)
    expect(readSelectedWorkspaceId()).toBe('ws-b')
  })

  it('reloadKeepsThePasskeyStep', async () => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.setItem(PENDING_KEY, 'Convidada')
    renderAccept()

    expect(
      await screen.findByRole('heading', { name: 'Crie sua passkey para voltar depois' }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey' }))

    expect(await screen.findByText('Início da família')).toBeInTheDocument()
    expect(registerPasskey).toHaveBeenCalledWith('Convidada')
    expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
  })

  it('acceptKeepsThePendingPasskeyUntilItIsCreated', async () => {
    mockState.signedIn = false
    seedMockInvitation({ token: TOKEN })
    renderAccept()

    fireEvent.change(await screen.findByLabelText('Como você se chama?'), {
      target: { value: 'Ana Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey neste aparelho' }))

    await screen.findByRole('heading', { name: 'Crie sua passkey para voltar depois' })
    expect(sessionStorage.getItem(PENDING_KEY)).toBe('Convidada')
  })

  it('recoveryGoesStraightToThePasskey', async () => {
    mockState.signedIn = false
    mockState.members['ws-b'] = [
      {
        id: 'm-bia',
        displayName: 'Bia Teste',
        nickname: 'Bia',
        role: 'ADULT',
        status: 'ACTIVE',
        you: false,
        passkeyOnly: true,
        joinedAt: '2026-11-01T12:00:00Z',
      },
    ]
    seedMockInvitation({
      token: TOKEN,
      kind: 'RECOVERY',
      nickname: null,
      targetMembershipId: 'm-bia',
    })
    renderAccept()

    expect(
      await screen.findByRole('heading', { name: 'Recuperar acesso de Bia' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Entrar com Google' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Como você se chama?')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Recuperar acesso' }))

    expect(
      await screen.findByRole('heading', { name: 'Crie sua passkey para voltar depois' }),
    ).toBeInTheDocument()
    expect(broadcastSignedIn).toHaveBeenCalledWith(mockState.user.id)
    expect(readSelectedWorkspaceId()).toBe('ws-b')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Criar passkey' }))
    expect(await screen.findByText('Início da família')).toBeInTheDocument()
    expect(registerPasskey).toHaveBeenCalledWith('Bia')
  })

  it('explainsWhyTheButtonsAreOffline', async () => {
    seedMockFamily()
    seedMockInvitation({ token: TOKEN })
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderAccept()

    expect(await screen.findByRole('button', { name: 'Entrar na família' })).toBeDisabled()
    expect(
      screen.getByText('Sem conexão. Dá para entrar quando o sinal voltar.'),
    ).toBeInTheDocument()
  })
})
