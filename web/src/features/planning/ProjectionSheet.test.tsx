import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import type { ProjectionView } from '@/shared/api/planning'
import { renderWithProviders } from '@/shared/testing'
import { ProjectionSheet } from './ProjectionSheet'

afterEach(cleanup)

const money = (amount: string) => ({ amount, currency: 'BRL' })

function view(hasHistory: boolean): ProjectionView {
  return {
    today: '2026-11-15',
    balanceToday: money('5000.00'),
    hasHistory,
    averageSpending: hasHistory ? money('1000.00') : null,
    points: [
      {
        monthEnd: '2026-11-30',
        balance: money('4600.00'),
        scheduled: money('600.00'),
        estimatedSpending: money('1000.00'),
      },
    ],
  }
}

describe('ProjectionSheet', () => {
  it('explains each part', async () => {
    seedMockFamily()
    renderWithProviders(<ProjectionSheet projection={view(true)} onClose={() => {}} />, {
      path: '/',
    })

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Saldo de hoje')).toBeInTheDocument()
    expect(within(dialog).getByText('R$ 5.000,00')).toBeInTheDocument()
    expect(within(dialog).getByText('Recorrências e previstos')).toBeInTheDocument()
    expect(within(dialog).getByText('R$ 600,00')).toBeInTheDocument()
    expect(within(dialog).getByText('Gasto estimado')).toBeInTheDocument()
    expect(within(dialog).getByText('−R$ 1.000,00')).toBeInTheDocument()
    expect(
      within(dialog).getByText(
        'É uma estimativa com a média dos gastos sem recorrência dos últimos 3 meses.',
      ),
    ).toBeInTheDocument()
  })

  it('without history note', async () => {
    seedMockFamily()
    renderWithProviders(<ProjectionSheet projection={view(false)} onClose={() => {}} />, {
      path: '/',
    })

    expect(
      await screen.findByText('Ainda não há 3 meses de gastos para estimar o resto.'),
    ).toBeInTheDocument()
  })
})
