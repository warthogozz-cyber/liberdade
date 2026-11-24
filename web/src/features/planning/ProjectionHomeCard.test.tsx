import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import ProjectionHomeCard from './ProjectionHomeCard'

afterEach(cleanup)

const money = (amount: string) => ({ amount, currency: 'BRL' })

function point(monthEnd: string, balance: string) {
  return {
    monthEnd,
    balance: money(balance),
    scheduled: money('0.00'),
    estimatedSpending: money('0.00'),
  }
}

function serve(balances: string[]) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning: true }
  const ends = ['2026-11-30', '2026-12-31', '2027-01-31', '2027-02-28']
  server.use(
    http.get(`${API_URL}/v1/workspaces/${workspace.id}/projection`, () =>
      HttpResponse.json({
        today: '2026-11-15',
        balanceToday: money('5000.00'),
        hasHistory: true,
        averageSpending: money('1000.00'),
        points: balances.map((balance, index) => point(ends[index], balance)),
      }),
    ),
  )
}

describe('ProjectionHomeCard', () => {
  it('shows four values labelled estimate', async () => {
    serve(['4000.00', '3000.00', '2000.00', '1000.00'])
    renderWithProviders(<ProjectionHomeCard />, { path: '/' })

    expect(await screen.findByText('Saldo previsto · estimativa')).toBeInTheDocument()
    expect(screen.getByText('R$ 4.000,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 3.000,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 2.000,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 1.000,00')).toBeInTheDocument()
    expect(screen.getByText('fim de nov')).toBeInTheDocument()
    expect(screen.getByText('fim de dez')).toBeInTheDocument()
  })

  it('keeps the heading and the list outside the button', async () => {
    serve(['4000.00', '3000.00'])
    renderWithProviders(<ProjectionHomeCard />, { path: '/' })

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Saldo previsto · estimativa' }),
    ).toBeInTheDocument()
    const card = screen.getByRole('heading', { level: 2 }).closest('section')
    expect(card).not.toBeNull()
    expect(within(card as HTMLElement).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Como chegamos nesses valores' })).toBeInTheDocument()
  })

  it('negative is not red', async () => {
    serve(['100.00', '-900.00'])
    renderWithProviders(<ProjectionHomeCard />, { path: '/' })

    const negative = await screen.findByText('−R$ 900,00')
    expect(negative.className).not.toMatch(/negative|danger|red/)
  })

  it('opens the explanation', async () => {
    serve(['4000.00'])
    renderWithProviders(<ProjectionHomeCard />, { path: '/' })

    fireEvent.click(await screen.findByRole('button', { name: 'Como chegamos nesses valores' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })
})
