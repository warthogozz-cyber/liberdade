import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { ActivityPage } from './ActivityPage'

afterEach(cleanup)

function add(overrides: Partial<(typeof mockState.activity)[number]>) {
  mockState.activity.push({
    id: crypto.randomUUID(),
    action: 'x',
    occurredAt: '2026-11-10T12:00:00Z',
    workspaceId: null,
    actorName: null,
    byMe: false,
    metadata: {},
    ...overrides,
  })
}

function renderActivity() {
  return renderWithProviders(<ActivityPage />, { path: '/mais/privacidade/atividade' })
}

describe('ActivityPage', () => {
  it('rendersActions', async () => {
    seedMockFamily()
    renderActivity()

    expect(await screen.findByText('Entrou com passkey')).toBeInTheDocument()
    expect(screen.getByText('Chrome no Android')).toBeInTheDocument()
    expect(screen.getByText('Você criou um convite como Adulto')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar para a privacidade' })).toHaveAttribute(
      'href',
      '/mais/privacidade',
    )
  })

  it('tellsEachEntryFromThePersonSide', async () => {
    seedMockFamily()
    mockState.activity = []
    add({ action: 'member.role_changed', byMe: true, actorName: 'Ana Teste', metadata: { to: 'VIEWER' } })
    add({ action: 'member.role_changed', actorName: 'Bia Teste', metadata: { to: 'VIEWER' } })
    add({ action: 'member.removed', actorName: null })
    add({ action: 'module.toggled', byMe: true, metadata: { key: 'planning', enabled: 'true' } })
    add({ action: 'data.exported', byMe: true, metadata: { scope: 'FAMILY' } })
    add({ action: 'member.removed', byMe: true, metadata: { reason: 'CONSENT_REVOKED' } })
    renderActivity()

    expect(
      await screen.findByText('Você mudou o papel de um membro para Leitor'),
    ).toBeInTheDocument()
    expect(screen.getByText('Bia Teste mudou seu papel para Leitor')).toBeInTheDocument()
    // A former member has no name left.
    expect(screen.getByText('Ex-membro tirou você da família')).toBeInTheDocument()
    expect(screen.getByText('Você ligou o módulo Planejamento')).toBeInTheDocument()
    expect(screen.getByText('Você baixou os dados da família')).toBeInTheDocument()
    expect(
      screen.getByText('O consentimento foi revogado e o Jovem saiu da família'),
    ).toBeInTheDocument()
  })

  it('tellsWhoActed', async () => {
    seedMockFamily()
    mockState.activity = []
    add({ action: 'auth.signed_in', byMe: true, metadata: { method: 'PASSKEY' } })
    add({ action: 'member.removed', actorName: 'Bia Teste', metadata: { reason: 'CONSENT_REVOKED' } })
    add({ action: 'recovery.used', actorName: null })
    renderActivity()

    expect(await screen.findByText('Por você')).toBeInTheDocument()
    expect(screen.getByText('Por Bia Teste')).toBeInTheDocument()
    expect(screen.getByText('Por Ex-membro')).toBeInTheDocument()
  })

  it('namesTheNewSignInMethods', async () => {
    seedMockFamily()
    mockState.activity = []
    add({ action: 'auth.signed_in', byMe: true, metadata: { method: 'INVITATION' } })
    add({ action: 'auth.signed_in', byMe: true, metadata: { method: 'RECOVERY' } })
    renderActivity()

    expect(await screen.findByText('Entrou pelo convite')).toBeInTheDocument()
    expect(screen.getByText('Entrou com o link de recuperação')).toBeInTheDocument()
  })

  it('omitsTheDeviceWhenOneHalfIsMissing', async () => {
    seedMockFamily()
    mockState.activity = []
    add({ action: 'auth.signed_in', byMe: true, metadata: { method: 'PASSKEY', browser: 'Chrome' } })
    add({ action: 'auth.signed_in', byMe: true, metadata: { method: 'GOOGLE', os: 'Android' } })
    renderActivity()

    expect(await screen.findByText('Entrou com passkey')).toBeInTheDocument()
    expect(screen.queryByText(/ no /)).not.toBeInTheDocument()
  })

  it('unknownActionHasAGenericLine', async () => {
    seedMockFamily()
    mockState.activity = []
    add({ action: 'something.new', byMe: true })
    renderActivity()

    expect(await screen.findByText('Alteração na conta')).toBeInTheDocument()
  })

  it('emptyHistory', async () => {
    seedMockFamily()
    mockState.activity = []
    renderActivity()

    expect(await screen.findByText('Nada por aqui ainda.')).toBeInTheDocument()
  })
})
