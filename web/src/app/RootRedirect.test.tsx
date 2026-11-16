import { cleanup, waitFor } from '@testing-library/react'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
import { saveReauthUser, saveReturnPath } from '@/shared/auth/returnPath'
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

  it('opens an invitation page even without a family', async () => {
    saveReturnPath('/convite?token=x')

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/convite'))
    expect(router.state.location.search).toBe('?token=x')
  })

  it('drops a family page without a family', async () => {
    saveReturnPath('/mais/modulos')

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/boas-vindas'))
    expect(sessionStorage.getItem('liberdade.returnPath')).toBeNull()
  })

  it('keeps going to the return path when Google brought back the same person', async () => {
    seedMockFamily()
    saveReturnPath('/mais/modulos')
    saveReauthUser(mockState.user.id)

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/mais/modulos'))
    expect(sessionStorage.getItem('liberdade.reauthUserId')).toBeNull()
  })

  it('clears the session and starts at /inicio when Google brought back another person', async () => {
    const sent = recordBroadcasts()
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { ...window.location, assign })
    seedMockFamily()
    saveReturnPath('/mais/modulos')
    saveReauthUser('0192f3a4-0000-7000-8000-0000000000ff')
    localStorage.setItem('liberdade.workspaceId', 'old')

    const { router, queryClient } = renderWithProviders(<App />, { path: '/*', route: '/' })
    queryClient.setQueryData(['sentinel'], 1)

    await waitFor(() => expect(assign).toHaveBeenCalledExactlyOnceWith('/inicio'))
    expect(router.state.location.pathname).not.toBe('/mais/modulos')
    expect(queryClient.getQueryData(['sentinel'])).toBeUndefined()
    expect(localStorage.getItem('liberdade.workspaceId')).toBeNull()
    expect(sent).toContainEqual({ type: 'signed-in', userId: mockState.user.id })
  })
})
