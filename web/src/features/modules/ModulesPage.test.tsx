import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { hasSeen, markSeen } from '@/shared/help'
import { registerModule, resetModules } from '@/shared/modules/registry'
import { renderWithProviders } from '@/shared/testing'
import { ModuleNotice, ModulesPage } from './ModulesPage'

afterEach(() => {
  cleanup()
  resetModules()
})

function renderModules() {
  return renderWithProviders(<ModulesPage />, { path: '/mais/modulos' })
}

describe('ModulesPage', () => {
  it('helpText', async () => {
    localStorage.setItem('liberdade.tips', 'on')
    seedMockFamily()
    renderModules()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'Ligue só o que quiser usar. Desligar esconde a parte, mas não apaga nada.',
    )
  })

  it('comingSoonDisabled', async () => {
    seedMockFamily()
    renderModules()

    expect(await screen.findByText('Núcleo')).toBeInTheDocument()
    expect(screen.getAllByText('Em breve')).toHaveLength(5)
    for (const name of ['Planejamento', 'Indicadores', 'Conquistas', 'Projetos', 'Feira']) {
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
    expect(screen.getAllByText('Em breve')).toHaveLength(4)

    fireEvent.click(toggle)

    await waitFor(() =>
      expect(screen.getByRole('switch', { name: 'Planejamento' })).toHaveAttribute(
        'aria-checked',
        'true',
      ),
    )
    expect(mockState.modules[workspace.id]?.planning).toBe(true)
  })

  it('switchIsDescribedByItsLine', async () => {
    seedMockFamily()
    renderModules()

    const toggle = await screen.findByRole('switch', { name: 'Planejamento' })
    expect(toggle).toHaveAccessibleDescription('Orçamentos, metas e a reserva de emergência.')
  })

  it.each(['VIEWER', 'YOUTH'] as const)('hasNoSwitchesFor%s', async (role) => {
    const workspace = seedMockFamily()
    workspace.role = role
    renderModules()

    expect(await screen.findByText('Planejamento')).toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
    expect(screen.getAllByText('Em breve')).toHaveLength(5)
  })

  it('enableShowsNoticeAndLink', async () => {
    seedMockFamily()
    registerModule({ key: 'markets', available: true })
    markSeen(mockState.user.id, 'tour-markets')
    renderModules()

    fireEvent.click(await screen.findByRole('switch', { name: 'Feira' }))

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Feira: ligado.'))
    const status = screen.getByRole('status')
    expect(within(status).getByRole('link', { name: 'Abrir Feira' })).toHaveAttribute(
      'href',
      '/mais/feira',
    )
    expect(hasSeen(mockState.user.id, 'tour-markets')).toBe(false)
  })

  it('youthGetsNoVenturesLink', async () => {
    seedMockFamily()
    registerModule({ key: 'ventures', available: true })
    renderModules()

    fireEvent.click(await screen.findByRole('switch', { name: 'Projetos' }))
    expect(await screen.findByRole('link', { name: 'Abrir Projetos' })).toHaveAttribute(
      'href',
      '/mais/projetos',
    )
    cleanup()

    renderWithProviders(<ModuleNotice module="ventures" role="YOUTH" />, { path: '/mais/modulos' })
    expect(screen.getByText('Projetos: ligado.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Abrir Projetos' })).not.toBeInTheDocument()
  })

  it('disableShowsNoNotice', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    registerModule({ key: 'planning', available: true })
    renderModules()

    const toggle = await screen.findByRole('switch', { name: 'Planejamento' })
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'))
    fireEvent.click(toggle)

    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'))
    expect(screen.queryByText(/ligado\./)).not.toBeInTheDocument()
  })
})
