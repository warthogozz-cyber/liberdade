import { cleanup, screen, waitFor } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { PlanningModuleRoute } from './PlanningModuleRoute'

afterEach(cleanup)

function App() {
  return (
    <Routes>
      <Route element={<PlanningModuleRoute />}>
        <Route path="/planejar" element={<p>Hub do planejamento</p>} />
      </Route>
      <Route path="/inicio" element={<p>Início</p>} />
    </Routes>
  )
}

describe('PlanningModuleRoute', () => {
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
})
