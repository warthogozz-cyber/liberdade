import { useState } from 'react'
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, type MeWorkspace } from '@/shared/api'
import { plannedView, renderWithProviders } from '@/shared/testing'
import { PlannedSheet } from './PlannedSheet'

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

function renderSheet(workspace: MeWorkspace) {
  const view = plannedView(
    {
      id: PLANNED_ID,
      kind: 'EXPENSE',
      amount: { amount: '1500.00', currency: 'BRL' },
      occurredOn: '2026-11-14',
      accountId: mockState.accounts[0].id,
      description: 'Aluguel',
    },
    '2026-11-14',
    { memberId: workspace.membershipId, version: 3 },
  )
  mockState.transactions.push(view)
  const onClose = vi.fn()
  // The screen that opens the sheet closes it: the sheet is gone once `onClose` is called.
  function Host() {
    const [open, setOpen] = useState(true)
    return open ? (
      <PlannedSheet
        workspace={workspace}
        planned={view}
        accounts={mockState.accounts}
        onClose={() => {
          onClose()
          setOpen(false)
        }}
      />
    ) : null
  }
  renderWithProviders(<Host />, { path: '/lancamentos' })
  return { onClose }
}

const sheet = async () => within(await screen.findByRole('dialog', { name: 'Previsto' }))
const toasts = () => within(screen.getByRole('region', { name: 'Notificações', hidden: true }))

describe('PlannedSheet', () => {
  it('confirms with the version and what was changed', async () => {
    const workspace = seedMockFamily()
    const captured: { body?: unknown } = {}
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/transactions/:id/confirm`, async ({ request }) => {
        captured.body = await request.clone().json()
      }),
    )
    const { onClose } = renderSheet(workspace)

    const dialog = await sheet()
    fireEvent.change(dialog.getByLabelText('Valor'), { target: { value: '1.620,50' } })
    fireEvent.click(dialog.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(captured.body).toEqual({
      version: 3,
      amount: { amount: '1620.50', currency: workspace.currency },
      occurredOn: '2026-11-14',
      accountId: mockState.accounts[0].id,
    })
  })

  it('skips, says so, and Desfazer restores it', async () => {
    const workspace = seedMockFamily()
    const restored: string[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/transactions/:id/restore`, ({ params }) => {
        restored.push(String(params.id))
      }),
    )
    const { onClose } = renderSheet(workspace)

    fireEvent.click((await sheet()).getByRole('button', { name: 'Pular' }))

    expect(await toasts().findByText('Previsto pulado')).toBeInTheDocument()
    expect(onClose).toHaveBeenCalled()
    fireEvent.click(toasts().getByRole('button', { name: 'Desfazer', hidden: true }))

    await waitFor(() => expect(restored).toEqual([PLANNED_ID]))
  })

  it.each([
    ['confirm', 'Confirmar', 409, 'transaction.version-conflict'],
    ['confirm', 'Confirmar', 409, 'transaction.not-planned'],
    ['skip', 'Pular', 404, 'transaction.not-found'],
    ['confirm', 'Confirmar', 404, 'transaction.not-found'],
  ])(
    'closes on a stale snapshot (%s, %i %s) and says it changed once the sheet is gone',
    async (action, label, status, code) => {
      const workspace = seedMockFamily()
      server.use(
        http.post(`${API_URL}/v1/workspaces/:ws/transactions/:id/${action}`, () =>
          problem(status, code, 'mudou'),
        ),
      )
      const { onClose } = renderSheet(workspace)

      fireEvent.click((await sheet()).getByRole('button', { name: label }))

      await waitFor(() => expect(onClose).toHaveBeenCalled())
      // No `hidden: true`: with the modal closed the notices are reachable.
      const region = await screen.findByRole('region', { name: 'Notificações' })
      expect(
        await within(region).findByText('Esse previsto mudou. Confira a lista.'),
      ).toBeInTheDocument()
      expect(screen.queryByRole('dialog', { name: 'Previsto' })).not.toBeInTheDocument()
    },
  )

  it('shows no buttons when a youth does not hold every account', async () => {
    const workspace = { ...seedMockFamily(), role: 'YOUTH' as const }
    const view = plannedView(
      {
        id: PLANNED_ID,
        kind: 'TRANSFER',
        amount: { amount: '50.00', currency: 'BRL' },
        occurredOn: '2026-11-14',
        accountId: '0192f3a4-0000-7000-8000-0000000000a9',
        toAccountId: mockState.accounts[0].id,
      },
      '2026-11-14',
      { memberId: workspace.membershipId, version: 0 },
    )
    renderWithProviders(
      <PlannedSheet
        workspace={workspace}
        planned={view}
        accounts={[mockState.accounts[0]]}
        onClose={vi.fn()}
      />,
      { path: '/lancamentos' },
    )

    const dialog = await sheet()
    expect(dialog.queryByRole('button', { name: 'Confirmar' })).not.toBeInTheDocument()
    expect(dialog.queryByRole('button', { name: 'Pular' })).not.toBeInTheDocument()
  })

  it('disables both buttons while offline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const workspace = seedMockFamily()
    renderSheet(workspace)

    const dialog = await sheet()
    expect(dialog.getByRole('button', { name: 'Confirmar' })).toBeDisabled()
    expect(dialog.getByRole('button', { name: 'Pular' })).toBeDisabled()
  })
})
