import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import ReserveHomeCard from './ReserveHomeCard'

afterEach(cleanup)

describe('ReserveHomeCard', () => {
  it('links to the screen', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    server.use(
      http.get(`${API_URL}/v1/workspaces/${workspace.id}/reserve`, () =>
        HttpResponse.json({
          balance: { amount: '12000.00', currency: 'BRL' },
          hasReserveAccounts: true,
          averageMonthlyCost: { amount: '3000.00', currency: 'BRL' },
          costSource: 'HISTORY',
          months: 4,
          targetMonths: 6,
          missing: { amount: '9000.00', currency: 'BRL' },
          manualMonthlyCost: null,
        }),
      ),
    )
    renderWithProviders(<ReserveHomeCard />, { path: '/' })

    expect(await screen.findByText('A reserva cobre 4,0 meses de gastos')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver reserva' })).toHaveAttribute(
      'href',
      '/planejar/reserva',
    )
  })

  describe('without a monthly cost', () => {
    function serveUnknownCost(role: 'OWNER' | 'VIEWER') {
      const workspace = seedMockFamily()
      workspace.role = role
      mockState.modules[workspace.id] = { planning: true }
      server.use(
        http.get(`${API_URL}/v1/workspaces/${workspace.id}/reserve`, () =>
          HttpResponse.json({
            balance: { amount: '0.00', currency: 'BRL' },
            hasReserveAccounts: true,
            averageMonthlyCost: null,
            costSource: 'NONE',
            months: null,
            targetMonths: 6,
            missing: null,
            manualMonthlyCost: null,
          }),
        ),
      )
    }

    it('asks the owner how much the family spends', async () => {
      serveUnknownCost('OWNER')
      renderWithProviders(<ReserveHomeCard />, { path: '/' })

      expect(
        await screen.findByText('Quanto a família gasta por mês, mais ou menos?'),
      ).toBeInTheDocument()
    })

    it('tells a viewer it is still missing instead of asking', async () => {
      serveUnknownCost('VIEWER')
      renderWithProviders(<ReserveHomeCard />, { path: '/' })

      expect(
        await screen.findByText('Ainda falta informar quanto a família gasta por mês.'),
      ).toBeInTheDocument()
      expect(
        screen.queryByText('Quanto a família gasta por mês, mais ou menos?'),
      ).not.toBeInTheDocument()
    })
  })
})
