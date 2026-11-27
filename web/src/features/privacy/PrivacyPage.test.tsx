import { startAuthentication } from '@simplewebauthn/browser'
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { PrivacyPage } from './PrivacyPage'

vi.mock('@simplewebauthn/browser')

const REAUTH = () => problem(403, 'auth.reauthentication-required', 'Confirme que é você.')

let clicked: { download: string; href: string }[] = []

beforeEach(() => {
  vi.mocked(startAuthentication).mockResolvedValue({ id: 'credential' } as never)
  clicked = []
  URL.createObjectURL = vi.fn(() => 'blob:test')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    clicked.push({ download: this.download, href: this.href })
  })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPrivacy() {
  return renderWithProviders(<PrivacyPage />, { path: '/mais/privacidade' })
}

const lastButton = (name: string) => screen.getAllByRole('button', { name }).at(-1)!

describe('PrivacyPage', () => {
  it('helpText', async () => {
    seedMockFamily()
    renderPrivacy()

    expect(
      await screen.findByText(
        'Aqui você baixa seus dados, vê quem entrou e pode excluir a conta. Excluir leva 30 dias e dá para cancelar.',
      ),
    ).toBeInTheDocument()
  })

  it('rendersActions', async () => {
    seedMockFamily()
    renderPrivacy()

    expect(await screen.findByRole('button', { name: 'Baixar meus dados' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Histórico de acessos' })).toHaveAttribute(
      'href',
      '/mais/privacidade/atividade',
    )
    expect(screen.getByRole('button', { name: 'Excluir minha conta' })).toBeInTheDocument()
    expect(await screen.findByText('Responsável legal pelo uso do app')).toBeInTheDocument()
  })

  it('downloadsZip', async () => {
    seedMockFamily()
    renderPrivacy()

    fireEvent.click(await screen.findByRole('button', { name: 'Baixar meus dados' }))

    await waitFor(() => expect(clicked).toHaveLength(1))
    expect(clicked[0]!.download).toMatch(/^liberdade-meus-dados-.*\.zip$/)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test'))
    // The temporary link does not stay on the page.
    expect(document.querySelector('a[download]')).toBeNull()
  })

  it('familyOnlyForOwner', async () => {
    const workspace = seedMockFamily()
    const view = renderPrivacy()
    expect(
      await screen.findByRole('button', { name: 'Baixar os dados da família' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Excluir a família' })).toBeInTheDocument()
    view.unmount()

    workspace.role = 'ADULT'
    renderPrivacy()
    expect(await screen.findByRole('button', { name: 'Baixar meus dados' })).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Baixar os dados da família' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Excluir a família' })).not.toBeInTheDocument()
  })

  it('offlineDisabled', async () => {
    seedMockFamily()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderPrivacy()

    // The family button appears once `me` has loaded.
    expect(await screen.findByRole('button', { name: 'Baixar os dados da família' })).toBeDisabled()
    expect(await screen.findByRole('button', { name: 'Baixar meus dados' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Baixar os dados da família' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Excluir a família' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Excluir minha conta' })).toBeDisabled()
    expect(screen.getByText(/Sem conexão/)).toBeInTheDocument()
  })

  it('soleOwnerMessage', async () => {
    seedMockFamily()
    server.use(
      http.post(`${API_URL}/v1/me/deletion`, () =>
        problem(409, 'account.sole-owner', 'Detalhe do servidor.'),
      ),
    )
    renderPrivacy()

    fireEvent.click(await screen.findByRole('button', { name: 'Excluir minha conta' }))
    await screen.findByRole('dialog')
    fireEvent.click(lastButton('Excluir minha conta'))

    expect(await screen.findByText(/único Dono de uma família/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir para a família' })).toHaveAttribute(
      'href',
      '/mais/familia',
    )
    expect(mockState.user.deletionScheduledFor).toBeNull()
  })

  it('schedulesAfterReauth', async () => {
    seedMockFamily()
    let calls = 0
    server.use(
      http.post(`${API_URL}/v1/me/deletion`, () => {
        calls += 1
        return calls === 1
          ? REAUTH()
          : HttpResponse.json({ deletionScheduledFor: '2026-12-16T12:00:00Z' }, { status: 202 })
      }),
    )
    renderPrivacy()

    fireEvent.click(await screen.findByRole('button', { name: 'Excluir minha conta' }))
    await screen.findByRole('dialog')
    fireEvent.click(lastButton('Excluir minha conta'))
    fireEvent.click(await screen.findByRole('button', { name: 'Usar passkey' }))

    await waitFor(() => expect(calls).toBe(2))
    expect(startAuthentication).toHaveBeenCalled()
  })

  it('needsTypedName', async () => {
    const workspace = seedMockFamily()
    renderPrivacy()

    fireEvent.click(await screen.findByRole('button', { name: 'Excluir a família' }))
    await screen.findByRole('dialog')
    const confirm = lastButton('Excluir a família')
    expect(confirm).toBeDisabled()

    const field = screen.getByLabelText(/Digite o nome da família/)
    fireEvent.change(field, { target: { value: 'família teste' } })
    expect(confirm).toBeDisabled()

    // The outer spaces do not count; the name must otherwise match exactly.
    fireEvent.change(field, { target: { value: `  ${workspace.name} ` } })
    expect(confirm).toBeEnabled()

    fireEvent.click(confirm)
    await waitFor(() => expect(workspace.deletionScheduledFor).not.toBeNull())
  })

  it('revokeWarns', async () => {
    seedMockFamily()
    renderPrivacy()

    fireEvent.click(await screen.findByRole('button', { name: 'Revogar' }))
    expect(await screen.findByText(/O Jovem sai da família/)).toBeInTheDocument()
    // Nothing is revoked until the person confirms.
    expect(mockState.consents[0]!.revokedAt).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Revogar e tirar o Jovem' }))

    expect(await screen.findByText('Consentimento revogado.')).toBeInTheDocument()
    expect(mockState.consents[0]!.revokedAt).not.toBeNull()
  })

  it('revokeNotOfferedWhenNotAllowed', async () => {
    seedMockFamily()
    mockState.consents[0]!.canRevoke = false
    renderPrivacy()

    expect(await screen.findByText('Responsável legal pelo uso do app')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revogar' })).not.toBeInTheDocument()
  })
})
