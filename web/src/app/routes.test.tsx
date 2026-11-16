import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'

afterEach(cleanup)

function App() {
  return useRoutes(routes)
}

function renderApp(route: string) {
  return renderWithProviders(<App />, { path: '/*', route })
}

describe('routes', () => {
  it('sends / to the welcome page without a family', async () => {
    const { router } = renderApp('/')

    await waitFor(() => expect(router.state.location.pathname).toBe('/boas-vindas'))
  })

  it('sends / to the home page with a family', async () => {
    seedMockFamily()
    const { router } = renderApp('/')

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
  })

  it('sends / to sign-in without a session', async () => {
    mockState.signedIn = false
    const { router } = renderApp('/')

    await waitFor(() => expect(router.state.location.pathname).toBe('/entrar'))
  })

  it('sends /inicio to the welcome page without a family', async () => {
    const { router } = renderApp('/inicio')

    await waitFor(() => expect(router.state.location.pathname).toBe('/boas-vindas'))
  })

  it('shows the main navigation on /inicio, with Início as the current page', async () => {
    seedMockFamily()
    renderApp('/inicio')

    const nav = await screen.findByRole('navigation', { name: 'Navegação principal' })
    const links = within(nav).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual([
      'Início',
      'Lançamentos',
      expect.stringContaining('Novo gasto'),
      'Mais',
    ])
    expect(within(nav).getByRole('link', { name: 'Início' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(within(nav).getByRole('link', { name: /Novo gasto/ })).toHaveAttribute('href', '/lancar')
    await waitFor(() => expect(document.title).toBe('Início · Liberdade'))
  })

  it('shows /lancar full screen, without the navigation', async () => {
    seedMockFamily()
    renderApp('/lancar')

    expect(await screen.findByRole('heading', { level: 1, name: 'Novo gasto' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('shows the not-found page with a link home for unknown paths', () => {
    renderApp('/nao-existe')

    expect(screen.getByRole('heading', { name: 'Página não encontrada.' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/')
  })
})
