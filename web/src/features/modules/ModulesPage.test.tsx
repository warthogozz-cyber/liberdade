import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { registerModule, resetModules } from '@/shared/modules/registry'
import { renderWithProviders } from '@/shared/testing'
import { ModulesPage } from './ModulesPage'

afterEach(() => {
  cleanup()
  resetModules()
})

function renderModules() {
  return renderWithProviders(<ModulesPage />, { path: '/mais/modulos' })
}

describe('ModulesPage', () => {
  it('comingSoonDisabled', async () => {
    seedMockFamily()
    renderModules()

    expect(await screen.findByText('Núcleo')).toBeInTheDocument()
    expect(screen.getAllByText('Em breve')).toHaveLength(4)
    for (const name of ['Planejamento', 'Indicadores', 'Conquistas', 'Projetos']) {
      expect(screen.getByRole('switch', { name })).toBeDisabled()
    }
  })

  it('availableModuleToggles', async () => {
    const workspace = seedMockFamily()
    registerModule({ key: 'planning', available: true })
    renderModules()

    const toggle = await screen.findByRole('switch', { name: 'Planejamento' })
    expect(toggle).toBeEnabled()
    expect(toggle).toHaveAttribute('aria-checked', 'false')
    expect(screen.getAllByText('Em breve')).toHaveLength(3)

    fireEvent.click(toggle)

    await waitFor(() =>
      expect(screen.getByRole('switch', { name: 'Planejamento' })).toHaveAttribute(
        'aria-checked',
        'true',
      ),
    )
    expect(mockState.modules[workspace.id]?.planning).toBe(true)
  })

  it('viewerHasNoSwitches', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'VIEWER'
    renderModules()

    expect(await screen.findByText('Planejamento')).toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
    expect(screen.getAllByText('Em breve')).toHaveLength(4)
  })
})
