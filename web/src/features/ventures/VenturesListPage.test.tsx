import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { foodSweetsVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { VenturesListPage } from './VenturesListPage'

beforeEach(() => {
  localStorage.setItem('liberdade.tips', 'on')
  Element.prototype.scrollIntoView = () => {}
})

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function render() {
  return renderWithProviders(<VenturesListPage />, { path: '/mais/projetos' })
}

describe('VenturesListPage', () => {
  it('openFirstClosedCollapsed', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    seedVentureFixture(workspace, {
      id: '0192f3a4-0000-7000-8000-0000000000a2',
      name: 'Bolsas antigas',
      status: 'CLOSED',
      closedOn: '2026-11-01',
    })
    render()

    expect(await screen.findByText('Doces caseiros')).toBeVisible()
    const group = screen.getByText('Encerrados (1)').closest('details')
    expect(group).not.toBeNull()
    expect(group).not.toHaveAttribute('open')
    expect(within(group as HTMLElement).getByText('Bolsas antigas')).not.toBeVisible()
  })

  it('showsProfitAndPayback', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    render()

    expect(await screen.findByText(/R\$\s540,00/)).toBeVisible()
    expect(screen.getByText('2 meses')).toBeVisible()
  })

  it('saysNeverPaysNeutrally', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace, {
      figures: { ...foodSweetsVenture().figures, paybackMonths: null, paybackState: 'NEVER' },
    })
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/ventures`, () =>
        HttpResponse.json({
          limitReached: false,
          ventures: [
            {
              ...foodSweetsVenture(),
              paybackMonths: null,
              paybackState: 'NEVER',
              monthlyProfit: { amount: '-20.00', currency: 'BRL' },
            },
          ],
        }),
      ),
    )
    render()

    const text = await screen.findByText('não se paga com esses números')
    expect(text.className).not.toMatch(/danger|error|red/)
    expect(screen.getByText(/−.*20,00/)).toBeVisible()
  })

  it('showsTheEmptyList', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/ventures`, () =>
        HttpResponse.json({ ventures: [], limitReached: false }),
      ),
    )
    render()

    expect(await screen.findByText('Nenhum projeto ainda.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Começar um projeto' })).toHaveAttribute(
      'href',
      '/mais/projetos/comecar',
    )
  })

  it('limitReachedShowsThePlanMessage', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/ventures`, () =>
        HttpResponse.json({ ventures: [], limitReached: true }),
      ),
    )
    render()

    const button = await screen.findByRole('button', { name: 'Começar um projeto' })
    expect(screen.queryByRole('link', { name: 'Começar um projeto' })).not.toBeInTheDocument()
    fireEvent.click(button)
    expect(
      await screen.findByText(
        /No plano Grátis, a família tem 1 projeto ativo\. Encerre um, ou veja o plano Família em/,
      ),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Mais > Plano' })).toHaveAttribute(
      'href',
      '/mais/plano',
    )
  })

  it('viewerCannotStart', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'VIEWER'
    seedVentureFixture(workspace)
    render()

    expect(await screen.findByText('Doces caseiros')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Começar um projeto' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Começar um projeto' })).not.toBeInTheDocument()
  })
})

describe('VenturesListPage tour', () => {
  const anchors = ['ventures-intro', 'ventures-start', 'ventures-actual']

  it('tourAnchors', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    render()
    await screen.findByText('Doces caseiros')
    for (const name of anchors) {
      expect(document.querySelector(`[data-tour="${name}"]`), name).not.toBeNull()
    }
    cleanup()

    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/ventures`, () =>
        HttpResponse.json({ ventures: [], limitReached: false }),
      ),
    )
    render()
    await screen.findByText('Nenhum projeto ainda.')
    for (const name of anchors) {
      expect(document.querySelector(`[data-tour="${name}"]`), name).not.toBeNull()
    }
  })

  it('viewerTourAsksForNothingTheyCannotDo', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'VIEWER'
    seedVentureFixture(workspace)
    render()
    await screen.findByText('Doces caseiros')

    const steps: string[] = []
    const tour = await screen.findByRole('dialog')
    await waitFor(() => expect(tour).toHaveTextContent('Passo 1 de 2'))
    steps.push(tour.textContent ?? '')
    fireEvent.click(within(tour).getByRole('button', { name: 'Próximo' }))
    expect(tour).toHaveTextContent('Passo 2 de 2')
    steps.push(tour.textContent ?? '')

    // Viewers read projects; tagging entries and starting a project are for the people who write.
    for (const step of steps) expect(step).not.toMatch(/Marque|Comece/)
  })
})
