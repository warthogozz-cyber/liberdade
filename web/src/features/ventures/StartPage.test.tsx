import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { StartPage } from './StartPage'

beforeEach(() => {
  localStorage.setItem('liberdade.tips', 'on')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function render() {
  seedVentureFixture(seedMockFamily())
  return renderWithProviders(<StartPage />, { path: '/mais/projetos/comecar' })
}

describe('StartPage', () => {
  it('fiveTracksAndBlank', async () => {
    render()

    const list = await screen.findByRole('list', { name: 'Trilhas' })
    const cards = within(list).getAllByRole('listitem')
    expect(cards).toHaveLength(5)
    const names = [
      'Culinária',
      'Produtos digitais',
      'Conteúdo',
      'Revenda e serviços',
      'Investimentos (conteúdo educativo)',
    ]
    names.forEach((name, index) => expect(cards[index]).toHaveTextContent(name))
    expect(screen.getByRole('button', { name: /Em branco/ })).toBeVisible()
  })

  it('resaleOpensTwoTemplates', async () => {
    render()

    const track = await screen.findByRole('button', { name: /Revenda e serviços/ })
    expect(track).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(track)

    expect(track).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('button', { name: 'Revenda de produtos' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Serviço por atendimento' })).toBeVisible()
  })

  it('investmentsOpensTheEducationalScreen', async () => {
    const { router } = render()

    const link = await screen.findByRole('link', { name: /Investimentos \(conteúdo educativo\)/ })
    fireEvent.click(link)

    expect(router.state.location.pathname).toBe('/mais/projetos/investimentos')
  })

  it('aSingleTemplateOpensTheSheetWithItsSummary', async () => {
    render()

    fireEvent.click(await screen.findByRole('button', { name: /Culinária/ }))

    const sheet = await screen.findByRole('dialog')
    expect(within(sheet).getByLabelText('Nome do projeto')).toHaveValue('Doces caseiros')
    expect(within(sheet).getByText(/R\$\s600,00/)).toBeVisible()
    expect(within(sheet).getByText(/R\$\s540,00/)).toBeVisible()
    expect(within(sheet).getByText('2 meses')).toBeVisible()
  })
})
