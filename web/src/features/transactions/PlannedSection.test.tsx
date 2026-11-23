import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, type MeWorkspace, type TransactionView } from '@/shared/api'
import { plannedView, renderWithProviders } from '@/shared/testing'
import { PlannedSection } from './PlannedSection'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

const PLANNED_ID = '0192f3a4-0000-7000-8000-0000000000d1'

/** A planned rent of R$ 1.500,00 for `scheduledOn`, at version 3, stored on the mock API. */
function planned(workspace: MeWorkspace, scheduledOn: string): TransactionView {
  const view = plannedView(
    {
      id: PLANNED_ID,
      kind: 'EXPENSE',
      amount: { amount: '1500.00', currency: 'BRL' },
      occurredOn: scheduledOn,
      accountId: mockState.accounts[0].id,
      description: 'Aluguel',
    },
    scheduledOn,
    { memberId: workspace.membershipId, version: 3 },
  )
  mockState.transactions.push(view)
  return view
}

function renderSection(workspace: MeWorkspace, items: TransactionView[]) {
  return renderWithProviders(
    <PlannedSection
      workspace={workspace}
      planned={items}
      month="2026-11"
      categories={new Map(mockState.categories.map((each) => [each.id, each]))}
      accounts={new Map(mockState.accounts.map((each) => [each.id, each]))}
    />,
    { path: '/lancamentos' },
  )
}

const toasts = () => within(screen.getByRole('region', { name: 'Notificações', hidden: true }))

describe('PlannedSection', () => {
  it('lists the planned ones under Previstos, with a neutral note for one that is overdue', async () => {
    const workspace = seedMockFamily()
    renderSection(workspace, [planned(workspace, '2026-11-14')])

    const section = within(await screen.findByRole('region', { name: 'Previstos' }))
    expect(section.getByText('Aluguel')).toBeInTheDocument()
    expect(section.getByText('−R$ 1.500,00')).toBeInTheDocument()
    expect(section.getByText('venceu em 14/11')).toBeInTheDocument()
  })

  it('shows the date, not a note, for one that is not due yet', async () => {
    const workspace = seedMockFamily()
    renderSection(workspace, [planned(workspace, '2026-11-20')])

    const section = within(await screen.findByRole('region', { name: 'Previstos' }))
    expect(section.getByText('20/11')).toBeInTheDocument()
    expect(section.queryByText(/venceu em/)).not.toBeInTheDocument()
  })

  it('confirms with the version it was shown at', async () => {
    const workspace = seedMockFamily()
    const captured: { body?: unknown } = {}
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/transactions/:id/confirm`, async ({ request }) => {
        captured.body = await request.clone().json()
      }),
    )
    renderSection(workspace, [planned(workspace, '2026-11-14')])

    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar Aluguel' }))

    await waitFor(() => expect(captured.body).toEqual({ version: 3 }))
  })

  it('disables Confirmar while offline and says why', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const workspace = seedMockFamily()
    renderSection(workspace, [planned(workspace, '2026-11-14')])

    expect(await screen.findByRole('button', { name: 'Confirmar Aluguel' })).toBeDisabled()
    expect(screen.getByText(/editar volta com o sinal/)).toBeInTheDocument()
  })

  it('says the planned one changed on a 409', async () => {
    const workspace = seedMockFamily()
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/transactions/:id/confirm`, () =>
        problem(409, 'transaction.version-conflict', 'mudou'),
      ),
    )
    renderSection(workspace, [planned(workspace, '2026-11-14')])

    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar Aluguel' }))

    expect(await toasts().findByText('Esse previsto mudou. Confira a lista.')).toBeInTheDocument()
  })

  it('has no buttons for a viewer', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'VIEWER'
    renderSection(workspace, [planned(workspace, '2026-11-14')])

    const section = within(await screen.findByRole('region', { name: 'Previstos' }))
    expect(section.getByText('Aluguel')).toBeInTheDocument()
    expect(section.queryByRole('button')).not.toBeInTheDocument()
  })

  it('renders nothing without planned ones', () => {
    const workspace = seedMockFamily()
    renderSection(workspace, [])

    expect(screen.queryByRole('region', { name: 'Previstos' })).not.toBeInTheDocument()
  })
})
