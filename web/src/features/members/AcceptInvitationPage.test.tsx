import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedMockInvitation } from '@/mocks/data'
import { registerPasskey } from '@/shared/auth/passkey'
import { readSelectedWorkspaceId } from '@/shared/api/workspaceChoice'
import { renderWithProviders } from '@/shared/testing'
import { AcceptInvitationPage } from './AcceptInvitationPage'

const assign = vi.hoisted(() => vi.fn<(url: string) => void>())

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

beforeEach(() => {
  sessionStorage.setItem(TOKEN_KEY, TOKEN)
  vi.mocked(registerPasskey).mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
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

    expect(await screen.findByText(/Bia Teste convidou você para a Família Bia Teste como Adulto/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Entrar na família' }))

    expect(await screen.findByText('Início da família')).toBeInTheDocument()
    expect(readSelectedWorkspaceId()).toBe('ws-b')
    expect(mockState.workspaces.some((workspace) => workspace.id === 'ws-b')).toBe(true)
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
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
})
