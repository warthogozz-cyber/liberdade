import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
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
