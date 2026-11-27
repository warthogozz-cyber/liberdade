import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type MeWorkspace } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { RecurrencesPage } from './RecurrencesPage'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

const RULE_ID = '0192f3a4-0000-7000-8000-0000000000c1'

function seedPlanning(role: MeWorkspace['role'] = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { planning: true }
  return workspace
}

/** An active monthly rent in the wallet, due next on 2026-12-10. */
function addRent() {
  mockState.rules.push({
    id: RULE_ID,
    accountId: mockState.accounts[0].id,
    amount: { amount: '1500.00', currency: 'BRL' },
    categoryId: mockState.categories.find((each) => each.name === 'Mercado')!.id,
    createdBy: mockState.user.id,
    description: 'Aluguel',
    endOn: null,
    frequency: 'MONTHLY',
    interval: 1,
    kind: 'EXPENSE',
    nextDueOn: '2026-12-10',
    paymentMethod: null,
    startOn: '2026-10-10',
    toAccountId: null,
    version: 0,
    archived: false,
  })
}

const renderRecurrences = () =>
  renderWithProviders(<RecurrencesPage />, { path: '/mais/recorrencias' })

describe('RecurrencesPage', () => {
  it('helpText', async () => {
    localStorage.setItem('liberdade.tips', 'on')
    seedPlanning()
    renderRecurrences()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'Previstos são contas que você espera pagar. Toque em Confirmar quando acontecerem.',
    )
  })

  it('lists the rules with their text and the next date', async () => {
    seedPlanning()
    addRent()
    renderRecurrences()

    const rent = await screen.findByRole('button', { name: /Aluguel/ })
    expect(within(rent).getByText('−R$ 1.500,00')).toBeInTheDocument()
    expect(within(rent).getByText('Todo mês, dia 10')).toBeInTheDocument()
    expect(within(rent).getByText('próximo: 10/12')).toBeInTheDocument()
    expect(document.title).toBe('Recorrências · Liberdade')
  })

  it('offers a new rule to the owner', async () => {
    seedPlanning('OWNER')
    renderRecurrences()

    expect(await screen.findByRole('button', { name: 'Nova recorrência' })).toBeInTheDocument()
  })

  it.each(['YOUTH', 'VIEWER'] as const)('shows the list only to a %s', async (role) => {
    const workspace = seedPlanning(role)
    mockState.accounts[0].ownerMembershipId = workspace.membershipId
    addRent()
    renderRecurrences()

    expect(await screen.findByText('Aluguel')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nova recorrência' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aluguel/ })).not.toBeInTheDocument()
  })

  it('creates a rule with the body of the API', async () => {
    const workspace = seedPlanning()
    const captured: { body?: unknown } = {}
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/recurring-rules`, async ({ request }) => {
        captured.body = await request.clone().json()
      }),
    )
    renderRecurrences()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova recorrência' }))
    const form = await screen.findByRole('dialog', { name: 'Nova recorrência' })
    fireEvent.change(within(form).getByLabelText('Valor'), { target: { value: '150,00' } })
    fireEvent.click(within(form).getByRole('radio', { name: /Mercado/ }))
    fireEvent.change(within(form).getByLabelText('Descrição (opcional)'), {
      target: { value: 'Mercado do mês' },
    })
    fireEvent.change(within(form).getByLabelText('Começa em'), { target: { value: '2026-11-20' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(captured.body).toBeDefined())
    expect(captured.body).toEqual({
      kind: 'EXPENSE',
      amount: { amount: '150.00', currency: workspace.currency },
      accountId: mockState.accounts[0].id,
      categoryId: mockState.categories.find((each) => each.name === 'Mercado')!.id,
      toAccountId: null,
      description: 'Mercado do mês',
      paymentMethod: null,
      frequency: 'MONTHLY',
      interval: 1,
      startOn: '2026-11-20',
      endOn: null,
    })
  })

  it('asks before archiving a rule', async () => {
    seedPlanning()
    addRent()
    let archived = false
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/recurring-rules/:id/archive`, () => {
        archived = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderRecurrences()

    fireEvent.click(await screen.findByRole('button', { name: /Aluguel/ }))
    const sheet = await screen.findByRole('dialog', { name: 'Editar recorrência' })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Arquivar recorrência' }))

    const confirm = await screen.findByRole('alertdialog')
    expect(archived).toBe(false)
    fireEvent.click(within(confirm).getByRole('button', { name: 'Arquivar recorrência' }))

    await waitFor(() => expect(archived).toBe(true))
  })
})
