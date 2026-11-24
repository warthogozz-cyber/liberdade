import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MorePage } from '@/app/MorePage'
import { createFamily, mockState, seedMockFamily } from '@/mocks/data'
import { readSelectedWorkspaceId } from '@/shared/api/workspaceChoice'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('MorePage', () => {
  it('linksToSettings', async () => {
    seedMockFamily()
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByRole('link', { name: 'Ajustes' })).toHaveAttribute(
      'href',
      '/mais/ajustes',
    )
  })

  it('linksToTheFamily', async () => {
    seedMockFamily()
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByRole('link', { name: 'Família' })).toHaveAttribute(
      'href',
      '/mais/familia',
    )
    // One family: nothing to switch.
    expect(screen.queryByText('Trocar de família')).not.toBeInTheDocument()
  })

  it('youthMenu', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'YOUTH'
    renderWithProviders(<MorePage />, { path: '/mais' })

    // The menu is static: wait for the family to load before checking what it leaves out.
    expect(await screen.findByText(workspace.name)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Família' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Contas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Categorias' })).not.toBeInTheDocument()
  })

  it('adultMenuKeepsAccountsAndCategories', async () => {
    const workspace = seedMockFamily()
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByText(workspace.name)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Contas' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Categorias' })).toBeInTheDocument()
  })

  it.each(['OWNER', 'ADULT', 'VIEWER', 'YOUTH'] as const)(
    'linksToModulesAndPrivacyFor%s',
    async (role) => {
      const workspace = seedMockFamily()
      workspace.role = role
      renderWithProviders(<MorePage />, { path: '/mais' })

      expect(await screen.findByText(workspace.name)).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Módulos' })).toHaveAttribute('href', '/mais/modulos')
      expect(screen.getByRole('link', { name: /^Privacidade$/ })).toHaveAttribute(
        'href',
        '/mais/privacidade',
      )
    },
  )

  it('hasPlanEntry', async () => {
    const workspace = seedMockFamily()
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByText(workspace.name)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Plano' })).toHaveAttribute('href', '/mais/plano')
  })

  it('switcherWithTwoFamilies', async () => {
    seedMockFamily()
    const other = createFamily('Família Bia Teste', 'America/Fortaleza')
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByText('Trocar de família')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Família Teste/ })).toHaveAttribute(
      'aria-current',
      'true',
    )

    fireEvent.click(screen.getByRole('button', { name: /Família Bia Teste/ }))

    expect(readSelectedWorkspaceId()).toBe(other.id)
    expect(mockState.workspaces).toHaveLength(2)
    expect(screen.getByRole('button', { name: /Família Bia Teste/ })).toHaveAttribute(
      'aria-current',
      'true',
    )
  })

  it.each(['OWNER', 'ADULT', 'VIEWER', 'YOUTH'] as const)(
    'indicatorsItemWhenOnFor%s',
    async (role) => {
      const workspace = seedMockFamily()
      workspace.role = role
      mockState.modules[workspace.id] = { indicators: true }
      renderWithProviders(<MorePage />, { path: '/mais' })

      expect(await screen.findByRole('link', { name: 'Indicadores' })).toHaveAttribute(
        'href',
        '/mais/indicadores',
      )
    },
  )

  it('noIndicatorsItemWhenOff', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { indicators: false }
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByText(workspace.name)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Indicadores' })).not.toBeInTheDocument()
  })

  it('no recurrences entry', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByText(workspace.name)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Recorrências' })).not.toBeInTheDocument()
  })
})
