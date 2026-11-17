import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily, seedPendingDocuments } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { countOutboxForUser, db } from '@/shared/offline'
import { outboxItem, outboxPayload, renderWithProviders } from '@/shared/testing'

// Unmounting stops the sync before the next test.
afterEach(cleanup)

const ID = '0192f3a4-5b6c-7d8e-9f01-23456789abcd'

function App() {
  return useRoutes(routes)
}

describe('SessionLayout documents', () => {
  it('gateWhenPending', async () => {
    seedMockFamily()
    seedPendingDocuments()
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Antes de começar' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação principal' }),
    ).not.toBeInTheDocument()
  })

  it('opensTheScreensWhileTheDocumentsLoad', async () => {
    seedMockFamily()
    seedPendingDocuments()
    server.use(
      http.get(`${API_URL}/v1/me/documents`, async () => {
        await delay('infinite')
      }),
    )
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Antes de começar')).not.toBeInTheDocument()
  })

  it('noGateWhenOffline', async () => {
    seedMockFamily()
    seedPendingDocuments()
    server.use(http.get(`${API_URL}/v1/me/documents`, () => HttpResponse.error()))
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Antes de começar')).not.toBeInTheDocument()
  })
})

describe('SessionLayout', () => {
  it('sends the user’s overdue queued expense once a screen opens', async () => {
    const workspace = seedMockFamily()
    await db.outbox.add(
      outboxItem(ID, {
        userId: mockState.user.id,
        workspaceId: workspace.id,
        payload: outboxPayload(ID, {
          accountId: mockState.accounts[0].id,
          categoryId: mockState.categories[0].id,
        }),
        sendAfter: 0,
      }),
    )

    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    await waitFor(() => expect(mockState.transactions.map((row) => row.id)).toEqual([ID]))
    await waitFor(async () => expect(await countOutboxForUser(mockState.user.id)).toBe(0))
  })

  it('shows loading, then the error with a retry that recovers', async () => {
    seedMockFamily()
    server.use(http.get(`${API_URL}/v1/me`, () => problem(500, 'server.error'), { once: true }))
    const { router } = renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(screen.getByRole('status')).toHaveTextContent('Carregando…')
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não deu para carregar. Tente de novo.',
    )
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/inicio')
  })
})

/** A BroadcastChannel that delivers to the other open channels of the same name, synchronously. */
class FakeBroadcastChannel {
  static open = new Set<FakeBroadcastChannel>()
  onmessage: ((event: MessageEvent) => void) | null = null
  name: string
  constructor(name: string) {
    this.name = name
    FakeBroadcastChannel.open.add(this)
  }
  postMessage(data: unknown) {
    for (const other of FakeBroadcastChannel.open) {
      if (other !== this && other.name === this.name) other.onmessage?.({ data } as MessageEvent)
    }
  }
  close() {
    FakeBroadcastChannel.open.delete(this)
  }
}

describe('SessionLayout across tabs', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    FakeBroadcastChannel.open.clear()
  })

  async function openHome() {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    seedMockFamily()
    const view = renderWithProviders(<App />, { path: '/*', route: '/inicio' })
    await screen.findByRole('navigation', { name: 'Navegação principal' })
    return view
  }

  it('signedOutElsewhere', async () => {
    const { router, queryClient } = await openHome()
    expect(queryClient.getQueryData(['me'])).toBeDefined()

    new FakeBroadcastChannel('liberdade-session').postMessage({ type: 'signed-out' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/entrar'))
    await waitFor(() => expect(queryClient.getQueryData(['me'])).toBeUndefined())
  })

  it('ignores a sign-in of the same person', async () => {
    const { router, queryClient } = await openHome()

    new FakeBroadcastChannel('liberdade-session').postMessage({
      type: 'signed-in',
      userId: mockState.user.id,
    })

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(router.state.location.pathname).toBe('/inicio')
    expect(queryClient.getQueryData(['me'])).toBeDefined()
  })

  it('clears the session and reloads at /inicio when another person signed in', async () => {
    const assign = vi.fn<(url: string) => void>()
    const { queryClient } = await openHome()
    vi.stubGlobal('location', { ...window.location, assign })

    new FakeBroadcastChannel('liberdade-session').postMessage({
      type: 'signed-in',
      userId: '0192f3a4-0000-7000-8000-0000000000ff',
    })

    await waitFor(() => expect(assign).toHaveBeenCalledExactlyOnceWith('/inicio'))
    expect(queryClient.getQueryData(['me'])).toBeUndefined()
  })
})
