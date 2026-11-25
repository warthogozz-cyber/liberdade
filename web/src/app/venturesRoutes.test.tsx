import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'

afterEach(cleanup)

describe('VenturesRoutes', () => {
  it('mapsEachSubPathToItsScreen', async () => {
    const workspace = seedMockFamily()
    const venture = seedVentureFixture(workspace)

    const list = renderWithProviders(null, { routes, route: '/mais/projetos' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Projetos' })).toBeInTheDocument()
    list.unmount()

    const start = renderWithProviders(null, { routes, route: '/mais/projetos/comecar' })
    expect(await screen.findByRole('list', { name: 'Trilhas' })).toBeInTheDocument()
    start.unmount()

    const edit = renderWithProviders(null, {
      routes,
      route: `/mais/projetos/${venture.id}/editar`,
    })
    expect(await screen.findByRole('textbox', { name: 'Nome do projeto' })).toHaveValue(
      venture.name,
    )
    edit.unmount()

    renderWithProviders(null, { routes, route: `/mais/projetos/${venture.id}` })
    expect(await screen.findByRole('heading', { level: 1, name: venture.name })).toBeInTheDocument()
  })
})
