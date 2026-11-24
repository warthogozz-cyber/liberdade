import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type MeWorkspace } from '@/shared/api'
import type { ReserveView } from '@/shared/api/planning'
import { renderWithProviders } from '@/shared/testing'
import { ReservePage } from './ReservePage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const money = (amount: string) => ({ amount, currency: 'BRL' })

function view(overrides: Partial<ReserveView> = {}): ReserveView {
  return {
    balance: money('12000.00'),
    hasReserveAccounts: true,
    averageMonthlyCost: money('3000.00'),
    costSource: 'HISTORY',
    months: 4,
    targetMonths: 6,
    missing: money('9000.00'),
    manualMonthlyCost: null,
    ...overrides,
  }
}

function setUp(reserve: ReserveView | undefined, role: MeWorkspace['role'] = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { planning: true }
  if (reserve) {
    server.use(
      http.get(`${API_URL}/v1/workspaces/${workspace.id}/reserve`, () =>
        HttpResponse.json(reserve),
      ),
    )
  }
  return workspace
}

const render = () => renderWithProviders(<ReservePage />, { path: '/planejar/reserva' })

describe('ReservePage', () => {
  it('shows coverage and missing', async () => {
    setUp(view())
    render()

    expect(await screen.findByText('A reserva cobre 4,0 meses de gastos')).toBeInTheDocument()
    expect(screen.getByText('Faltam R$ 9.000,00 para 6 meses')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Reserva' })).toHaveAttribute('aria-valuenow', '67')
    expect(screen.getByText(/Uma reserva de emergência é um dinheiro guardado/)).toBeInTheDocument()
  })

  it('says so when the target is met', async () => {
    setUp(view({ months: 7, missing: money('0.00') }))
    render()

    expect(await screen.findByText('A reserva já cobre a meta de 6 meses')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Reserva' })).toHaveAttribute('aria-valuenow', '100')
  })

  it('asks for monthly cost', async () => {
    setUp(
      view({
        costSource: 'NONE',
        months: null,
        missing: null,
        averageMonthlyCost: null,
        hasReserveAccounts: false,
      }),
    )
    const bodies: unknown[] = []
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PATCH') bodies.push(await request.clone().json())
    })
    render()

    expect(
      await screen.findByText('Quanto a família gasta por mês, mais ou menos?'),
    ).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Gasto mensal da família'), {
      target: { value: '2.500,00' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({ manualMonthlyCost: { amount: '2500.00', currency: 'BRL' } })
    server.events.removeAllListeners()
  })

  it('changes the target with the selector', async () => {
    setUp(undefined)
    render()

    const select = await screen.findByLabelText('Meta da reserva')
    expect(select).toHaveValue('6')
    expect(screen.getAllByRole('option')).toHaveLength(24)
    fireEvent.change(select, { target: { value: '12' } })

    await waitFor(() => expect(mockState.reserve.targetMonths).toBe(12))
  })

  it('viewer cannot edit', async () => {
    setUp(view(), 'VIEWER')
    render()

    expect(await screen.findByText('A reserva cobre 4,0 meses de gastos')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Salvar' })).not.toBeInTheDocument()
  })

  it('no reserve accounts hint', async () => {
    setUp(view({ hasReserveAccounts: false, months: 0, missing: money('18000.00') }))
    render()

    expect(await screen.findByText('Marque uma conta como reserva em Contas.')).toBeInTheDocument()
  })

  it('offline turns the controls off and says why', async () => {
    setUp(view())
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    render()

    expect(
      await screen.findByText('Sem conexão. A reserva precisa de internet para mudar.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Meta da reserva')).toBeDisabled()
  })
})
