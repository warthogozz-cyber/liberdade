import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'

afterEach(cleanup)

async function navLinks() {
  const nav = await screen.findByRole('navigation', { name: 'Navegação principal' })
  return within(nav).getAllByRole('link')
}

describe('AppShell', () => {
  it('shows Planejar when module is on', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    renderWithProviders(null, { routes, route: '/inicio' })

    await screen.findByRole('link', { name: 'Planejar' })
    const links = await navLinks()
    expect(links.map((link) => link.textContent)).toEqual([
      'Início',
      'Lançamentos',
      expect.stringContaining('Novo gasto'),
      'Planejar',
      'Mais',
    ])
    expect(screen.getByRole('link', { name: 'Planejar' })).toHaveAttribute('href', '/planejar')
  })

  it('four items when module is off', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: false }
    renderWithProviders(null, { routes, route: '/inicio' })

    const links = await navLinks()
    expect(links).toHaveLength(4)
    expect(screen.queryByRole('link', { name: 'Planejar' })).not.toBeInTheDocument()
  })
})
