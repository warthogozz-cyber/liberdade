import { cleanup, screen, waitFor } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, queryKeys } from '@/shared/api'
import { createTestQueryClient, renderWithProviders } from '@/shared/testing'
import { ModuleRoute } from './ModuleRoute'

afterEach(cleanup)

function App() {
  return (
    <Routes>
      <Route
        path="/planejar"
        element={
          <ModuleRoute module="planning">
            <p>Hub do planejamento</p>
          </ModuleRoute>
        }
      />
      <Route
        path="/mais/indicadores"
        element={
          <ModuleRoute module="indicators">
            <p>Indicadores</p>
          </ModuleRoute>
        }
      />
      <Route path="/inicio" element={<p>Início</p>} />
    </Routes>
  )
}

describe('ModuleRoute', () => {
  it('sends the family home while the planning module is off', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: false }

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/planejar' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(screen.queryByText('Hub do planejamento')).toBeNull()
  })

  it('shows the screen while the planning module is on', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/planejar' })

    expect(await screen.findByText('Hub do planejamento')).toBeTruthy()
    expect(router.state.location.pathname).toBe('/planejar')
  })

  it('checks the module it is given', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true, indicators: false }

    const { router } = renderWithProviders(<App />, {
      path: '/*',
      route: '/mais/indicadores',
    })

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(screen.queryByText('Indicadores')).toBeNull()
  })

  it('shows nothing while the module list loads', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/modules`, async () => {
        await delay(150)
        return HttpResponse.json([{ key: 'planning', enabled: true }])
      }),
    )

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/planejar' })

    expect(screen.queryByText('Hub do planejamento')).toBeNull()
    expect(router.state.location.pathname).toBe('/planejar')
    expect(await screen.findByText('Hub do planejamento')).toBeTruthy()
  })

  it('asks again before sending the family home on a cached list that says off', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    // The list saved on the device (or read a moment ago) still says off: the family has just
    // switched the module on.
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.modules(workspace.id), [{ key: 'planning', enabled: false }])

    const { router } = renderWithProviders(<App />, {
      path: '/*',
      route: '/planejar',
      queryClient,
    })

    expect(await screen.findByText('Hub do planejamento')).toBeTruthy()
    expect(router.state.location.pathname).toBe('/planejar')
  })

  it('sends the family home on the cached list when the server cannot be reached', async () => {
    const workspace = seedMockFamily()
    server.use(http.get(`${API_URL}/v1/workspaces/:ws/modules`, () => HttpResponse.error()))
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.modules(workspace.id), [{ key: 'planning', enabled: false }])

    const { router } = renderWithProviders(<App />, {
      path: '/*',
      route: '/planejar',
      queryClient,
    })

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(screen.queryByText('Hub do planejamento')).toBeNull()
  })
})
