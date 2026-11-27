import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { glossary } from '@/shared/help'
import { renderWithProviders } from '@/shared/testing'
import { HowItWorksPage } from './HowItWorksPage'

afterEach(cleanup)

const ALL_ON = {
  planning: true,
  indicators: true,
  gamification: true,
  markets: true,
  ventures: true,
} as const

function renderPage(
  role: 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER',
  modules: Partial<Record<keyof typeof ALL_ON, boolean>> = ALL_ON,
) {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = modules
  return renderWithProviders(<HowItWorksPage />, { path: '/mais/como-funciona' })
}

const sectionTitles = () =>
  screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)

describe('HowItWorksPage', () => {
  it('sectionsInOrder', async () => {
    renderPage('OWNER')

    expect(await screen.findByRole('heading', { level: 2, name: 'Projetos' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Como funciona' })).toBeInTheDocument()
    expect(sectionTitles()).toEqual([
      'Começar',
      'Papéis',
      'Sem internet',
      'Ao vivo',
      'Planejamento',
      'Indicadores',
      'Conquistas',
      'Feira',
      'Projetos',
      'Palavras',
    ])
  })

  it('onlyEnabledModules', async () => {
    renderPage('OWNER', { ...ALL_ON, markets: false })

    expect(await screen.findByRole('heading', { level: 2, name: 'Projetos' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 2, name: 'Feira' })).not.toBeInTheDocument()
  })

  it('youthDoesNotSeeVentures', async () => {
    renderPage('YOUTH', { ventures: true, markets: true })

    expect(await screen.findByRole('heading', { level: 2, name: 'Feira' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 2, name: 'Projetos' })).not.toBeInTheDocument()
  })

  it('viewerSeesEnabledModules', async () => {
    renderPage('VIEWER', { indicators: true, ventures: true })

    expect(await screen.findByRole('heading', { level: 2, name: 'Projetos' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Indicadores' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 2, name: 'Feira' })).not.toBeInTheDocument()
  })

  it('seeTourNavigatesWithState', async () => {
    const { router } = renderPage('ADULT')

    const planning = await screen.findByRole('region', { name: 'Planejamento' })
    fireEvent.click(within(planning).getByRole('link', { name: 'Ver o tour' }))

    expect(router.state.location.pathname).toBe('/planejar')
    expect(router.state.location.state).toEqual({ forceTour: 'planning' })
  })

  it('noTourLinkWithoutSteps', async () => {
    renderPage('OWNER')

    const feira = await screen.findByRole('region', { name: 'Feira' })
    expect(within(feira).queryByRole('link', { name: 'Ver o tour' })).not.toBeInTheDocument()
    const planning = screen.getByRole('region', { name: 'Planejamento' })
    expect(within(planning).getByRole('link', { name: 'Ver o tour' })).toBeInTheDocument()
  })

  it('glossaryHasEveryTerm', async () => {
    const { container } = renderPage('OWNER')

    const words = await screen.findByRole('region', { name: 'Palavras' })
    expect(words.querySelector('dl')).not.toBeNull()
    expect(container.querySelectorAll('dt')).toHaveLength(glossary.length)
    expect(container.querySelectorAll('dd')).toHaveLength(glossary.length)
    expect(within(words).getByText('FGC')).toBeInTheDocument()
    expect(within(words).getByText(/R\$ 250 mil/)).toBeInTheDocument()
  })

  // axe is not a unit-test dependency: it runs on this screen in e2e/a11y.spec.ts, in both themes
  // and with the real styles. This checks the structure the screen relies on.
  it('accessibleStructure', async () => {
    renderPage('OWNER')

    await screen.findByRole('heading', { level: 2, name: 'Projetos' })
    for (const region of screen.getAllByRole('region')) {
      expect(region).toHaveAccessibleName()
    }
    for (const link of screen.getAllByRole('link')) {
      expect(link).toHaveAccessibleName()
      expect(link.className).toContain('min-h-12')
    }
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  })
})
