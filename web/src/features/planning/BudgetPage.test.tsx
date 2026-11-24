import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { BudgetPage } from './BudgetPage'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function category(name: string) {
  const found = mockState.categories.find((each) => each.name === name)
  if (!found) throw new Error(`seed has no ${name}`)
  return found
}

/** A family with a R$ 1.200,00 limit on Mercado in `month` and R$ `spent` spent there. */
function setUp(spent: string, month = '2026-11') {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning: true }
  const mercado = category('Mercado')
  mockState.budgets.push({ month, categoryId: mercado.id, limitCents: 120_000n, version: 0 })
  mockState.transactions.push({
    id: crypto.randomUUID(),
    kind: 'EXPENSE',
    amount: { amount: spent, currency: 'BRL' },
    occurredOn: `${month}-02`,
    accountId: mockState.accounts[0].id,
    toAccountId: null,
    categoryId: mercado.id,
    description: null,
    paymentMethod: null,
    tags: [],
    status: 'CONFIRMED',
    scheduledOn: null,
    recurringRuleId: null,
    createdAt: new Date().toISOString(),
    memberId: workspace.membershipId,
    version: 0,
  })
  return { workspace, mercado }
}

const render = () => renderWithProviders(<BudgetPage />, { path: '/planejar/orcamento' })

describe('BudgetPage', () => {
  it('line shows text with color', async () => {
    setUp('1260.00')
    render()

    expect(await screen.findByText('Passou R$ 60,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 1.260,00 de R$ 1.200,00')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Mercado' })).toHaveAttribute('aria-valuenow', '105')
  })

  it('near and at say it in words', async () => {
    setUp('960.00')
    render()

    expect(await screen.findByText('Chegou a 80% do limite')).toBeInTheDocument()
    expect(screen.getByText('R$ 960,00 de R$ 1.200,00')).toBeInTheDocument()
    expect(screen.getByText('80%')).toBeInTheDocument()
  })

  it('version conflict reloads', async () => {
    const { workspace, mercado } = setUp('100.00')
    let reads = 0
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'GET' && request.url.includes('/budgets?')) reads += 1
    })
    server.use(
      http.put(`${API_URL}/v1/workspaces/${workspace.id}/budgets/2026-11/${mercado.id}`, () =>
        problem(409, 'budget.version-conflict', 'Outra pessoa mudou o limite.'),
      ),
    )
    render()

    fireEvent.click(await screen.findByRole('button', { name: /Mercado/ }))
    const dialog = await screen.findByRole('dialog')
    const field = within(dialog).getByLabelText('Limite do mês')
    fireEvent.change(field, { target: { value: '1.500,00' } })
    const before = reads
    fireEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Esse limite mudou. Confira o valor.')).toBeInTheDocument()
    await waitFor(() => expect(reads).toBeGreaterThan(before))
    server.events.removeAllListeners()
  })

  it('copy from previous month', async () => {
    setUp('100.00', '2026-10')
    render()

    expect(await screen.findByText('Nenhum limite neste mês.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Copiar do mês anterior' }))

    expect(await screen.findByText('1 limite copiado')).toBeInTheDocument()
    expect(mockState.budgets.filter((each) => each.month === '2026-11')).toHaveLength(1)
  })

  it('sets a limit on a category without one', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Definir limite' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Categoria'), {
      target: { value: category('Mercado').id },
    })
    fireEvent.change(within(dialog).getByLabelText('Limite do mês'), {
      target: { value: '500,00' },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(mockState.budgets).toHaveLength(1))
    expect(mockState.budgets[0].limitCents).toBe(50_000n)
  })

  it('offline turns the buttons off and says why', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    render()

    expect(
      await screen.findByText('Sem conexão. Limites precisam de internet.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Definir limite' })).toBeDisabled()
  })
})
