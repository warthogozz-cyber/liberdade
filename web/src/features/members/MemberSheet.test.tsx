import { startAuthentication } from '@simplewebauthn/browser'
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
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

function renderSheet(target: MemberView, viewerRole: MemberRole = 'OWNER') {
  const workspace = seedMockFamily()
  mockState.members[workspace.id]!.push(target)
  return renderWithProviders(
    <MemberSheet
      workspaceId={workspace.id}
      viewerRole={viewerRole}
      member={target}
      onClose={() => {}}
    />,
  )
}

describe('MemberSheet', () => {
  it('roleChangeUsesReauth', async () => {
    let calls = 0
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/members/:id`, () => {
        calls += 1
        return calls === 1
          ? problem(403, 'auth.reauthentication-required', 'Confirme que é você.')
          : HttpResponse.json(member({ role: 'VIEWER' }))
      }),
    )
    renderSheet(member())

    fireEvent.change(screen.getByLabelText('Papel'), { target: { value: 'VIEWER' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    await waitFor(() => expect(calls).toBe(2))
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

    expect(screen.queryByRole('button', { name: 'Gerar link de recuperação' })).not.toBeInTheDocument()
    // The same person with only a passkey does get it.
    cleanup()
    renderSheet(member({ id: 'm-other', passkeyOnly: true }))
    expect(screen.getByRole('button', { name: 'Gerar link de recuperação' })).toBeInTheDocument()
  })
})
