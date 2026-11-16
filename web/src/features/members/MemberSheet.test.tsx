import { startAuthentication } from '@simplewebauthn/browser'
import { cleanup, fireEvent, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, type MemberRole, type MemberView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { MemberSheet } from './MemberSheet'

vi.mock('@simplewebauthn/browser')

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

beforeEach(() => {
  vi.mocked(startAuthentication).mockResolvedValue({ id: 'credential' } as never)
})

function member(overrides: Partial<MemberView> = {}): MemberView {
  return {
    id: 'm-bia',
    displayName: 'Bia Teste',
    nickname: 'Bia',
    role: 'ADULT',
    status: 'ACTIVE',
    you: false,
    passkeyOnly: true,
    joinedAt: '2026-11-01T12:00:00Z',
    ...overrides,
  }
}

function renderSheet(
  target: MemberView,
  viewerRole: MemberRole = 'OWNER',
  onClose: () => void = () => {},
) {
  const workspace = seedMockFamily()
  mockState.members[workspace.id]!.push(target)
  return renderWithProviders(
    <MemberSheet
      workspaceId={workspace.id}
      viewerRole={viewerRole}
      member={target}
      onClose={onClose}
    />,
  )
}

const REAUTH = () => problem(403, 'auth.reauthentication-required', 'Confirme que é você.')

describe('MemberSheet', () => {
  it('roleChangeUsesReauth', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/members/:id`, async ({ request }) => {
        bodies.push(await request.json())
        return bodies.length === 1 ? REAUTH() : HttpResponse.json(member({ role: 'VIEWER' }))
      }),
    )
    const onClose = vi.fn()
    renderSheet(member(), 'OWNER', onClose)

    fireEvent.change(screen.getByLabelText('Papel'), { target: { value: 'VIEWER' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    // The role changed: the second request carried it, and the sheet reported and closed.
    expect(await screen.findByText('Alterações salvas.')).toBeInTheDocument()
    expect(bodies).toHaveLength(2)
    expect(bodies[1]).toMatchObject({ role: 'VIEWER' })
    expect(startAuthentication).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('removeUsesReauth', async () => {
    let calls = 0
    server.use(
      http.delete(`${API_URL}/v1/workspaces/:ws/members/:id`, () => {
        calls += 1
        return calls === 1 ? REAUTH() : new HttpResponse(null, { status: 204 })
      }),
    )
    const onClose = vi.fn()
    renderSheet(member(), 'OWNER', onClose)

    fireEvent.click(screen.getByRole('button', { name: 'Remover da família' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Remover da família' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    expect(await screen.findByText('Pessoa removida.')).toBeInTheDocument()
    expect(calls).toBe(2)
    expect(startAuthentication).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('removeShowsTheLastOwnerMessage', async () => {
    server.use(
      http.delete(`${API_URL}/v1/workspaces/:ws/members/:id`, () =>
        problem(422, 'member.last-owner', 'Detalhe do servidor.'),
      ),
    )
    const onClose = vi.fn()
    renderSheet(member({ role: 'OWNER' }), 'OWNER', onClose)

    fireEvent.click(screen.getByRole('button', { name: 'Remover da família' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Remover da família' }))

    expect(await screen.findByText('A família precisa de pelo menos um Dono.')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('recoveryLinkUsesReauth', async () => {
    let calls = 0
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/members/:id/recovery`, () => {
        calls += 1
        return calls === 1
          ? REAUTH()
          : HttpResponse.json(
              {
                id: 'inv-1',
                kind: 'RECOVERY',
                nickname: null,
                role: 'ADULT',
                createdByName: null,
                expiresAt: '2026-11-20T12:00:00Z',
                token: 'R'.repeat(43),
              },
              { status: 201 },
            )
      }),
    )
    renderSheet(member())

    fireEvent.click(screen.getByRole('button', { name: 'Gerar link de recuperação' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    expect(await screen.findByLabelText('Link do convite')).toHaveValue(
      `${window.location.origin}/convite#${'R'.repeat(43)}`,
    )
    expect(
      screen.getByText(
        'O link vale por 24 horas e funciona uma vez só. Entregue só à própria pessoa.',
      ),
    ).toBeInTheDocument()
    expect(calls).toBe(2)
    expect(startAuthentication).toHaveBeenCalled()
  })

  it('lastOwnerMessage', async () => {
    const owner = member({ id: 'm-owner', role: 'OWNER', nickname: 'Dona' })
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/members/:id`, () =>
        problem(422, 'member.last-owner', 'Detalhe do servidor.'),
      ),
    )
    renderSheet(owner)

    fireEvent.change(screen.getByLabelText('Papel'), { target: { value: 'ADULT' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('A família precisa de pelo menos um Dono.')).toBeInTheDocument()
  })

  it('recoveryOnlyForPasskeyOnly', () => {
    renderSheet(member({ passkeyOnly: false }))

    expect(
      screen.queryByRole('button', { name: 'Gerar link de recuperação' }),
    ).not.toBeInTheDocument()
    // The same person with only a passkey does get it.
    cleanup()
    renderSheet(member({ id: 'm-other', passkeyOnly: true }))
    expect(screen.getByRole('button', { name: 'Gerar link de recuperação' })).toBeInTheDocument()
  })
})
