import { cleanup, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { IndicatorsPage } from './IndicatorsPage'

afterEach(cleanup)

function seedOn() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { indicators: true }
  return workspace
}

function render() {
  return renderWithProviders(<IndicatorsPage />, { path: '/mais/indicadores' })
}

describe('IndicatorsPage', () => {
  it('everyCardHasASourceStamp', async () => {
    seedOn()
    render()

    const stamps = await screen.findAllByText(/^Fonte:/)
    expect(stamps).toHaveLength(5)
    for (const stamp of stamps) {
      expect(stamp).toHaveTextContent('Fonte: Banco Central do Brasil · atualizado em 24/11/2026')
    }
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(5)
    expect(screen.getByText('15,00% a.a.')).toBeInTheDocument()
    expect(screen.getByText('+0,25 p.p. em 05/11')).toBeInTheDocument()
    expect(screen.getAllByText('dado de outubro de 2026')).toHaveLength(2)
    expect(screen.getAllByText('dado de 24/11/2026')).toHaveLength(3)
    expect(screen.getByRole('link', { name: 'Calcular rendimento real' })).toHaveAttribute(
      'href',
      '/mais/indicadores/calculadora',
    )
  })

  it('showsTheDisclaimer', async () => {
    seedOn()
    render()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'Conteúdo educativo. Não é recomendação de investimento.',
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Indicadores' })).toBeInTheDocument()
  })

  it('staleCardKeepsTheValue', async () => {
    seedOn()
    mockState.indicators[1] = { ...mockState.indicators[1]!, stale: true }
    render()

    const card = (await screen.findByRole('heading', { name: 'CDI' })).closest('li')!
    expect(within(card).getByText('14,90% a.a.')).toBeInTheDocument()
    expect(within(card).getByText('desatualizado')).toBeInTheDocument()
  })

  it('cardWithoutPoint', async () => {
    seedOn()
    mockState.indicators[0] = {
      ...mockState.indicators[0]!,
      value: null,
      referenceDate: null,
      variation: null,
      updatedAt: null,
      source: { name: 'Banco Central do Brasil', url: null },
    }
    render()

    const card = (await screen.findByRole('heading', { name: 'Selic (meta)' })).closest('li')!
    expect(within(card).getByText('Sem dados ainda')).toBeInTheDocument()
    expect(within(card).getByText(/ainda sem atualização/)).toBeInTheDocument()
  })

  it('variationIsNeutral', async () => {
    seedOn()
    render()

    for (const text of ['+0,25 p.p. em 05/11', '−0,10 p.p. em relação a setembro de 2026']) {
      const element = await screen.findByText(text)
      expect(element.className).not.toMatch(/danger|success|red|green/)
    }
  })

  it('showsTheErrorWithRetry', async () => {
    seedOn()
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/indicators`, () =>
        HttpResponse.json({ code: 'internal' }, { status: 500 }),
      ),
    )
    render()

    expect(await screen.findByText('Não deu para carregar. Tente de novo.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
  })

  it('footerLinksToTheRobotPage', async () => {
    seedOn()
    render()

    expect(await screen.findByRole('link', { name: 'Como coletamos' })).toHaveAttribute(
      'href',
      '/robo',
    )
  })
})
