import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { useRoutes, type RouteObject } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
import { db } from '@/shared/offline'
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
    // A lazy route loads only under a data router.
    renderWithProviders(null, { routes, route: '/inicio' })

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

  it('links the accounts from Mais', async () => {
    seedMockFamily()
    renderWithProviders(null, { routes, route: '/mais' })

    expect(await screen.findByRole('link', { name: 'Contas' })).toHaveAttribute(
      'href',
      '/mais/contas',
    )
  })

  it('opens the accounts on /mais/contas', async () => {
    seedMockFamily()
    renderWithProviders(null, { routes, route: '/mais/contas' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Contas' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /Carteira/ })).toBeInTheDocument()
  })

  it("saves the family's data to the device once it has loaded, for a reload offline (I1)", async () => {
    const family = seedMockFamily()
    renderApp('/inicio')

    await waitFor(async () => {
      const saved = await db.kv.get('liberdade-query-cache')
      const { clientState } = JSON.parse(saved?.value ?? '{"clientState":{"queries":[]}}') as {
        clientState: { queries: { queryKey: unknown[] }[] }
      }
      expect(clientState.queries.map(({ queryKey }) => queryKey.slice(2, 3)[0])).toEqual(
        expect.arrayContaining(['accounts', 'categories', 'dashboard', 'transactions']),
      )
      expect(
        clientState.queries.every(
          ({ queryKey }) => queryKey[0] !== 'ws' || queryKey[1] === family.id,
        ),
      ).toBe(true)
    })
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
    expect(document.title).toBe('Página não encontrada · Liberdade')
  })
})

describe('lazy routes', () => {
  function findRoute(list: RouteObject[], path: string): RouteObject | undefined {
    for (const route of list) {
      if (route.path === path) return route
      const found = route.children && findRoute(route.children, path)
      if (found) return found
    }
    return undefined
  }

  it('lazyRoutesHaveNoStaticComponent', () => {
    for (const path of ['/inicio', '/entrar', '/lancamentos', '/mais']) {
      const route = findRoute(routes, path)
      expect(route?.lazy, path).toBeTypeOf('function')
      expect(route?.Component, path).toBeUndefined()
      expect(route?.element, path).toBeUndefined()
    }
  })

  it('firstExpensePathStaysStatic', () => {
    const route = findRoute(routes, '/lancar')
    expect(route?.lazy).toBeUndefined()
    expect(route?.Component ?? route?.element).toBeDefined()
  })

  it('lazyRouteRenders', async () => {
    seedMockFamily()
    renderWithProviders(null, { routes, route: '/lancamentos' })

    expect(await screen.findByRole('heading', { name: 'Lançamentos' })).toBeInTheDocument()
  })
})
