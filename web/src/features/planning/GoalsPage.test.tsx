import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { renderWithProviders } from '@/shared/testing'
import { GoalsPage } from './GoalsPage'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function addGoal(
  name: string,
  savedAmount: string,
  extra: { linkedAccountId?: string; dueOn?: string } = {},
) {
  mockState.goals.push({
    id: crypto.randomUUID(),
    name,
    icon: 'travel',
    targetCents: 100_000n,
    currency: 'BRL',
    dueOn: extra.dueOn ?? null,
    linkedAccountId: extra.linkedAccountId ?? null,
    achievedAt: null,
    archived: false,
    version: 0,
    contributions: [
      {
        id: crypto.randomUUID(),
        amount: { amount: savedAmount, currency: 'BRL' },
        memberId: null,
        occurredOn: '2026-11-10',
      },
    ],
  })
}

function setUp(role: 'OWNER' | 'YOUTH' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { planning: true }
  return workspace
}

const render = () => renderWithProviders(<GoalsPage />, { path: '/planejar/metas' })

describe('GoalsPage', () => {
  it('lists a goal with its saved amount, deadline and suggestion', async () => {
    setUp()
    addGoal('Viagem', '250.00', { dueOn: '2027-08' })
    render()

    expect(await screen.findByText('Viagem')).toBeInTheDocument()
    expect(screen.getByText('R$ 250,00 de R$ 1.000,00')).toBeInTheDocument()
    expect(screen.getByText('até ago/2027')).toBeInTheDocument()
    expect(screen.getByText(/Sugestão: R\$ \d/)).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Viagem' })).toHaveAttribute('aria-valuenow', '25')
  })

  it('opening celebrates once', async () => {
    setUp()
    addGoal('Viagem', '1000.00')
    const first = render()

    expect(await screen.findByText('Meta atingida: Viagem!')).toBeInTheDocument()
    first.unmount()

    render()
    expect(await screen.findByText('Viagem')).toBeInTheDocument()
    expect(screen.queryByText('Meta atingida: Viagem!')).not.toBeInTheDocument()
    expect(screen.getByText('Atingida')).toBeInTheDocument()
  })

  it('limit reached explains first', async () => {
    setUp()
    addGoal('A', '1.00')
    addGoal('B', '1.00')
    addGoal('C', '1.00')
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova meta' }))
    const dialog = await screen.findByRole('dialog')

    expect(
      within(dialog).getByText(/No plano Grátis, a família tem até 3 metas ativas/),
    ).toBeVisible()
    expect(within(dialog).queryByLabelText('Nome')).not.toBeInTheDocument()
  })

  it('creates a goal', async () => {
    setUp()
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova meta' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Nome'), { target: { value: 'Casa' } })
    fireEvent.change(within(dialog).getByLabelText('Valor da meta'), {
      target: { value: '12.000,00' },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Casa')).toBeInTheDocument()
    expect(mockState.goals).toHaveLength(1)
  })

  it('youth sees no actions', async () => {
    setUp('YOUTH')
    addGoal('Viagem', '100.00', { linkedAccountId: mockState.accounts[0].id })
    const urls: string[] = []
    server.events.on('request:start', ({ request }) => urls.push(request.url))
    render()

    expect(await screen.findByText('Viagem')).toBeInTheDocument()
    expect(screen.getByText('Acompanha o saldo de uma conta da família')).toBeInTheDocument()
    expect(screen.queryByText(mockState.accounts[0].name)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nova meta' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Arquivar' })).not.toBeInTheDocument()
    expect(urls.filter((each) => each.includes('/accounts'))).toEqual([])
    server.events.removeAllListeners()
  })

  it('offline turns the actions off and says why', async () => {
    setUp()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    render()

    expect(await screen.findByText('Sem conexão. Metas precisam de internet.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nova meta' })).toBeDisabled()
  })
})
