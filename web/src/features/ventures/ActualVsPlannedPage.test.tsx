import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { prdExampleVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
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
    expect(link).toHaveAttribute('href', `/lancamentos?projeto=${venture.id}`)
    expect(await screen.findByRole('table')).toBeVisible()
    expect(screen.getByText('Status do projeto: Em andamento')).toBeVisible()
  })
})
