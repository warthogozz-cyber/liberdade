import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { TransactionsPage } from './TransactionsPage'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('TransactionFilters tag and payment method', () => {
  it('lists the family tags and methods, and the query carries the choices', async () => {
    const workspace = seedMockFamily()
    const escola = { id: crypto.randomUUID(), name: 'Escola', archivedAt: null }
    mockState.tags.push(escola)
    const queries: URLSearchParams[] = []
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/transactions`, ({ request }) => {
        queries.push(new URL(request.url).searchParams)
        return HttpResponse.json([])
      }),
    )
    renderWithProviders(<TransactionsPage />, { path: '/lancamentos' })

    const open = async () => {
      fireEvent.click(await screen.findByRole('button', { name: 'Filtros' }))
      const dialog = await screen.findByRole('dialog', { name: 'Filtros' })
      return {
        tag: within(await within(dialog).findByRole('group', { name: 'Tag' })),
        method: within(within(dialog).getByRole('group', { name: 'Forma de pagamento' })),
      }
    }

    const first = await open()
    expect(first.method.getAllByRole('radio')).toHaveLength(workspace.paymentMethods.length)
    fireEvent.click(first.tag.getByRole('radio', { name: 'Escola' }))
    // Each tap applies and closes the sheet; the second choice is made on a reopened one.
    const second = await open()
    fireEvent.click(second.method.getByRole('radio', { name: 'Pix' }))

    await waitFor(() => {
      const last = queries.at(-1)
      expect(last?.get('tagId')).toBe(escola.id)
      expect(last?.get('paymentMethod')).toBe('PIX')
    })
  })
})
