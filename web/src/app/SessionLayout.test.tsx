import { cleanup, waitFor } from '@testing-library/react'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
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
})
