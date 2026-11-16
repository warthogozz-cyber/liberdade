import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { GettingStartedCard } from '@/features/dashboard/GettingStartedCard'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, queryKeys, type MeView } from '@/shared/api'
import { db } from '@/shared/offline'
import { createTestQueryClient, outboxItem, renderWithProviders } from '@/shared/testing'

const DISMISS = `${API_URL}/v1/me/getting-started/dismiss`

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function meView(): MeView {
  return {
    user: mockState.user,
    workspaces: mockState.workspaces,
    suggestedWorkspaceName: mockState.suggestedWorkspaceName,
  }
}

/** A family that has a transaction, so the card is due. Extra accounts are stored as given. */
function family({ hasTransactions = true, archived = 0, active = 0 } = {}) {
  const workspace = seedMockFamily()
  workspace.hasTransactions = hasTransactions
  const wallet = mockState.accounts[0]!
  for (let i = 0; i < active; i++) {
    mockState.accounts.push({ ...wallet, id: `extra-${i}`, name: `Extra ${i}` })
  }
  for (let i = 0; i < archived; i++) {
    mockState.accounts.push({
      ...wallet,
      id: `old-${i}`,
      name: `Antiga ${i}`,
      archivedAt: '2026-10-01T12:00:00Z',
    })
  }
  return workspace
}

/** Renders the card with the `me` of the session already cached, as Home has it. */
function renderCard({ cacheAccounts = false } = {}) {
  const queryClient = createTestQueryClient()
  queryClient.setQueryData(queryKeys.me, meView())
  if (cacheAccounts) {
    // Offline, the accounts come from the device's cache.
    queryClient.setQueryData(
      queryKeys.accounts(mockState.workspaces[0]!.id),
      mockState.accounts.map((account) => ({ ...account, balance: account.initialBalance })),
    )
  }
  return renderWithProviders(
    <GettingStartedCard workspace={mockState.workspaces[0]!} me={meView()} />,
    { path: '/inicio', queryClient },
  )
}

describe('GettingStartedCard', () => {
  it('hiddenBeforeFirstTransaction', async () => {
    family({ hasTransactions: false })
    renderCard()

    await waitFor(() => expect(screen.queryByText('Primeiros passos')).not.toBeInTheDocument())
  })

  it('queuedItemShowsCard', async () => {
    const workspace = family({ hasTransactions: false })
    await db.outbox.add(outboxItem('t1', { userId: mockState.user.id, workspaceId: workspace.id }))
    renderCard()

    expect(await screen.findByRole('heading', { name: 'Primeiros passos' })).toBeInTheDocument()
  })

  it('accountsDoneAtTwo', async () => {
    family({ archived: 1 })
    renderCard()

    const accounts = (await screen.findByText('Adicionar suas contas')).closest('li')!
    expect(accounts).toHaveTextContent('✓')
    expect(accounts).toHaveTextContent('Feito')
    const passkey = screen.getByText('Criar passkey neste aparelho').closest('li')!
    expect(passkey).not.toHaveTextContent('Feito')
    expect(screen.getByRole('link', { name: 'Criar passkey neste aparelho' })).toHaveAttribute(
      'href',
      '/mais',
    )
  })

  it('linksToAccounts', async () => {
    family()
    renderCard()

    expect(await screen.findByRole('link', { name: 'Adicionar suas contas' })).toHaveAttribute(
      'href',
      '/mais/contas',
    )
  })

  it('hiddenWhenAllDone', async () => {
    const workspace = family({ active: 1 })
    workspace.familyInvited = true
    mockState.user.passkeyCreatedAt = '2026-11-01T12:00:00Z'
    renderCard()

    await waitFor(() => expect(screen.queryByText('Primeiros passos')).not.toBeInTheDocument())
    // Give the accounts time to arrive: it must stay empty after them too.
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })

  it('dismissCallsApi', async () => {
    const workspace = family()
    renderCard()

    fireEvent.click(await screen.findByRole('button', { name: 'Fechar primeiros passos' }))

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Primeiros passos' })).not.toBeInTheDocument(),
    )
    // The mock API stamps the family the POST names, and answers 404 for any other.
    await waitFor(() => expect(workspace.gettingStartedDismissedAt).not.toBeNull())
    expect(screen.queryByRole('heading', { name: 'Primeiros passos' })).not.toBeInTheDocument()
  })

  it('dismissFailureRestores', async () => {
    family()
    server.use(http.post(DISMISS, () => problem(500, 'internal.error')))
    renderCard()

    fireEvent.click(await screen.findByRole('button', { name: 'Fechar primeiros passos' }))

    expect(await screen.findByText('Não deu para fechar agora. Tente de novo.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Primeiros passos' })).toBeInTheDocument()
  })

  it('offlineDisablesClose', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    family()
    renderCard({ cacheAccounts: true })

    const close = await screen.findByRole('button', { name: 'Fechar primeiros passos' })
    expect(close).toBeDisabled()
    expect(close).toHaveAttribute('title', 'Sem conexão. Dá para fechar quando o sinal voltar.')
    expect(
      screen.getByText('Sem conexão. Dá para fechar quando o sinal voltar.'),
    ).toBeInTheDocument()
  })
})

describe('GettingStartedCard invite step', () => {
  it('inviteItem', async () => {
    const workspace = family()
    const view = renderCard()

    expect(await screen.findByRole('link', { name: 'Convidar a família' })).toHaveAttribute(
      'href',
      '/mais/familia/convidar',
    )

    // Once someone was invited the step is done.
    view.unmount()
    workspace.familyInvited = true
    renderCard()
    const invite = (await screen.findByText('Convidar a família')).closest('li')!
    expect(invite).toHaveTextContent('Feito')

    // A Viewer cannot invite, so is not asked to.
    cleanup()
    workspace.role = 'VIEWER'
    renderCard()
    await screen.findByRole('heading', { name: 'Primeiros passos' })
    expect(screen.queryByText('Convidar a família')).not.toBeInTheDocument()
  })

  it('inviteStepKeepsTheCardUntilDone', async () => {
    // Accounts and passkey are done; an Owner who invited nobody is still asked to.
    const workspace = family({ active: 1 })
    mockState.user.passkeyCreatedAt = '2026-11-01T12:00:00Z'
    const view = renderCard()

    const invite = (await screen.findByText('Convidar a família')).closest('li')!
    expect(invite).not.toHaveTextContent('Feito')

    // Once someone is invited, nothing is left and the card goes.
    view.unmount()
    workspace.familyInvited = true
    renderCard()
    await waitFor(() => expect(screen.queryByText('Primeiros passos')).not.toBeInTheDocument())
  })

  it('viewerIsNotHeldByTheInviteStep', async () => {
    // A Viewer cannot invite, so the step never keeps their card open.
    const workspace = family({ active: 1 })
    workspace.role = 'VIEWER'
    mockState.user.passkeyCreatedAt = '2026-11-01T12:00:00Z'
    renderCard()

    await waitFor(() => expect(screen.queryByText('Primeiros passos')).not.toBeInTheDocument())
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})
