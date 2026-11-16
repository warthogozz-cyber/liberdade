import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedMockInvitation } from '@/mocks/data'
import type { MemberView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { FamilyPage } from './FamilyPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function addMember(workspaceId: string, overrides: Partial<MemberView> & { id: string }) {
  const member: MemberView = {
    displayName: 'Bia Teste',
    nickname: null,
    role: 'ADULT',
    status: 'ACTIVE',
    you: false,
    passkeyOnly: false,
    joinedAt: '2026-11-01T12:00:00Z',
    ...overrides,
  }
  mockState.members[workspaceId]!.push(member)
  return member
}

function renderFamily() {
  return renderWithProviders(<FamilyPage />, { path: '/mais/familia' })
}

describe('FamilyPage', () => {
  it('ownerSeesManagement', async () => {
    const workspace = seedMockFamily()
    addMember(workspace.id, { id: 'm-bia', nickname: 'Bia' })
    seedMockInvitation({ token: 'B'.repeat(43), workspaceId: workspace.id, nickname: 'Convidada' })
    renderFamily()

    expect(await screen.findByRole('button', { name: /Bia/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Renomear' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Convidar alguém' })).toHaveAttribute(
      'href',
      '/mais/familia/convidar',
    )
    expect(await screen.findByRole('button', { name: 'Revogar' })).toBeInTheDocument()
    expect(screen.getByText('Convites pendentes')).toBeInTheDocument()
    // The signed-in person's own row says so.
    expect(screen.getByText(/Você/)).toBeInTheDocument()
  })

  it('viewerSeesListOnly', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'VIEWER'
    mockState.members[workspace.id]![0]!.role = 'VIEWER'
    addMember(workspace.id, { id: 'm-bia', nickname: 'Bia', role: 'OWNER' })
    renderFamily()

    expect(await screen.findByText('Bia')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Renomear' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Convidar alguém' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revogar' })).not.toBeInTheDocument()
    // The rows are plain text: nothing opens.
    expect(screen.queryByRole('button', { name: /Bia/ })).not.toBeInTheDocument()
  })

  it('removedCollapsed', async () => {
    const workspace = seedMockFamily()
    addMember(workspace.id, { id: 'm-old', nickname: 'Antiga', status: 'REMOVED' })
    renderFamily()

    const section = (await screen.findByText('Quem saiu')).closest('details')!
    expect(section).not.toHaveAttribute('open')
    expect(within(section).getByText('Antiga')).toBeInTheDocument()
    // The removed person is not among the active members.
    expect(screen.getAllByText('Antiga')).toHaveLength(1)
  })

  it('namesAnInvitationWithoutANickname', async () => {
    const workspace = seedMockFamily()
    seedMockInvitation({ token: 'D'.repeat(43), workspaceId: workspace.id, nickname: null })
    renderFamily()

    expect(await screen.findByText('Convite sem apelido')).toBeInTheDocument()
  })

  it('explainsWhyRenameAndRevokeAreOffline', async () => {
    const workspace = seedMockFamily()
    seedMockInvitation({ token: 'E'.repeat(43), workspaceId: workspace.id })
    renderFamily()
    const revoke = await screen.findByRole('button', { name: 'Revogar' })
    expect(
      screen.queryByText('Sem conexão. Mudar a família volta com o sinal.'),
    ).not.toBeInTheDocument()

    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    window.dispatchEvent(new Event('offline'))

    expect(
      await screen.findByText('Sem conexão. Mudar a família volta com o sinal.'),
    ).toBeInTheDocument()
    expect(revoke).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Renomear' })).toBeDisabled()
  })
})
