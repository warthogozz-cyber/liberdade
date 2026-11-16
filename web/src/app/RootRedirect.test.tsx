import { cleanup, waitFor } from '@testing-library/react'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
import { saveReturnPath } from '@/shared/auth'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function App() {
  return useRoutes(routes)
}

/** Records what this tab tells the others. */
function recordBroadcasts() {
  const sent: unknown[] = []
  vi.stubGlobal(
    'BroadcastChannel',
    class {
      postMessage(data: unknown) {
        sent.push(data)
      }
      close() {}
    },
  )
  return sent
}

describe('RootRedirect', () => {
  it('opens the home page and tells the other tabs who signed in', async () => {
    const sent = recordBroadcasts()
    seedMockFamily()

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(sent).toContainEqual({ type: 'signed-in', userId: mockState.user.id })
  })

  it('opens the welcome page while there is no family', async () => {
    const { router } = renderWithProviders(<App />, { path: '/*', route: '/' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/boas-vindas'))
  })

  it('opens the page a re-authentication came from, once', async () => {
    seedMockFamily()
    saveReturnPath('/mais/modulos')

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/mais/modulos'))
    expect(sessionStorage.getItem('liberdade.returnPath')).toBeNull()
  })
})
