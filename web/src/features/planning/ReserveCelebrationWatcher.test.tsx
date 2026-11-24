import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { act } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { emitLocalAction } from '@/shared/events'
import { ModuleSlot } from '@/shared/modules'
import { renderWithProviders } from '@/shared/testing'
import ReserveCelebrationWatcher from './ReserveCelebrationWatcher'
import './register'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function setUp(role: 'OWNER' | 'YOUTH' | 'VIEWER' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.workspaces[0].role = role
  mockState.modules[workspace.id] = { planning: true }
  server.use(
    http.get(`${API_URL}/v1/workspaces/${workspace.id}/reserve`, () =>
      HttpResponse.json({
        balance: { amount: '19000.00', currency: 'BRL' },
        hasReserveAccounts: true,
        averageMonthlyCost: { amount: '3000.00', currency: 'BRL' },
        costSource: 'HISTORY',
        months: 6.3,
        targetMonths: 6,
        missing: { amount: '0.00', currency: 'BRL' },
        manualMonthlyCost: null,
      }),
    ),
  )
  return workspace
}

const saved = (workspaceId: string, fromQueue: boolean) =>
  act(() => emitLocalAction({ type: 'transaction-saved', workspaceId, fromQueue }))

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

describe('ReserveCelebrationWatcher', () => {
  it('celebratesAfterLocalSave', async () => {
    const workspace = setUp()
    renderWithProviders(<ReserveCelebrationWatcher />)

    saved(workspace.id, false)

    expect(await screen.findByText('Reserva completa: 6 meses guardados!')).toBeInTheDocument()
  })

  it('ignoresQueuedActions', async () => {
    const workspace = setUp()
    renderWithProviders(<ReserveCelebrationWatcher />)

    saved(workspace.id, true)
    await pause(250)

    expect(screen.queryByText(/Reserva completa/)).not.toBeInTheDocument()
  })

  it('mountsInTheWorkspaceSlotForTheOwner', async () => {
    const workspace = setUp('OWNER')
    renderWithProviders(<ModuleSlot name="workspace" />, { path: '/inicio' })

    // The slot loads lazily: save again until the watcher is listening.
    await vi.waitFor(async () => {
      saved(workspace.id, false)
      expect(await screen.findByText(/Reserva completa/, {}, { timeout: 200 })).toBeInTheDocument()
    })
  })

  it.each(['YOUTH', 'VIEWER'] as const)('notForYouthOrViewer (%s)', async (role) => {
    const workspace = setUp(role)
    renderWithProviders(<ModuleSlot name="workspace" />, { path: '/inicio' })

    saved(workspace.id, false)
    await pause(250)

    expect(screen.queryByText(/Reserva completa/)).not.toBeInTheDocument()
  })
})
