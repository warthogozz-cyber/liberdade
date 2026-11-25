import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { prdExampleVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { ActualVsPlannedPage } from './ActualVsPlannedPage'

afterEach(cleanup)

describe('ActualVsPlannedPage', () => {
  it('linksToFilteredTransactions', async () => {
    const workspace = seedMockFamily()
    const venture = prdExampleVenture({ status: 'ACTIVE', startedOn: '2026-10-01' })
    seedVentureFixture(workspace, venture)
    renderWithProviders(<ActualVsPlannedPage />, {
      path: '/mais/projetos/:id/real',
      route: `/mais/projetos/${venture.id}/real`,
    })

    const link = await screen.findByRole('link', { name: 'Ver lançamentos do projeto' })
    expect(link).toHaveAttribute(
      'href',
      `/lancamentos?projeto=${venture.id}&mes=${new Date().toISOString().slice(0, 7)}`,
    )
    expect(await screen.findByRole('table')).toBeVisible()
    expect(screen.getByText('Status do projeto: Em andamento')).toBeVisible()
  })

  it('opensTheNewestMonthOfAClosedProject', async () => {
    const workspace = seedMockFamily()
    const venture = prdExampleVenture({ status: 'CLOSED', startedOn: '2026-03-01' })
    seedVentureFixture(workspace, venture)
    const brl = (amount: string) => ({ amount, currency: 'BRL' })
    const month = (value: string) => ({
      month: value,
      income: brl('0.00'),
      expense: brl('0.00'),
      result: brl('0.00'),
      plannedIncome: null,
      plannedCost: null,
      plannedResult: null,
      comparison: null,
      difference: null,
    })
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/ventures/:id/actual-vs-planned`, () =>
        HttpResponse.json({
          currency: 'BRL',
          planned: {
            initialInvestment: venture.figures.initialInvestment,
            paybackMonths: venture.figures.paybackMonths,
            paybackState: venture.figures.paybackState,
          },
          real: { state: 'NO_DATA', months: null, investment: null, missing: null },
          months: [month('2026-05'), month('2026-04'), month('2026-03')],
          totals: { ...month('2026-05'), month: undefined },
        }),
      ),
    )
    renderWithProviders(<ActualVsPlannedPage />, {
      path: '/mais/projetos/:id/real',
      route: `/mais/projetos/${venture.id}/real`,
    })

    const link = await screen.findByRole('link', { name: 'Ver lançamentos do projeto' })
    expect(link).toHaveAttribute('href', `/lancamentos?projeto=${venture.id}&mes=2026-05`)
  })
})
