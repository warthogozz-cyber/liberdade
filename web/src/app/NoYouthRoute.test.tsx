import { cleanup, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { NoYouthRoute } from './NoYouthRoute'

afterEach(cleanup)

function App() {
  return (
    <Routes>
      <Route element={<NoYouthRoute />}>
        <Route path="/mais/projetos" element={<p>Lista de projetos</p>} />
      </Route>
      <Route path="/mais" element={<p>Mais</p>} />
    </Routes>
  )
}

describe('NoYouthRoute', () => {
  it('youthGoesBackToMore', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'YOUTH'

    const { router } = renderWithProviders(<App />, { path: '/*', route: '/mais/projetos' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/mais'))
    expect(screen.queryByText('Lista de projetos')).toBeNull()
  })

  it('rendersTheChildForAnAdult', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'ADULT'

    renderWithProviders(<App />, { path: '/*', route: '/mais/projetos' })

    expect(await screen.findByText('Lista de projetos')).toBeTruthy()
  })
})
