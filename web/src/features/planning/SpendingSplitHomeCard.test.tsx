import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import SpendingSplitHomeCard from './SpendingSplitHomeCard'

afterEach(cleanup)

describe('SpendingSplitHomeCard', () => {
  it('shows both totals', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { planning: true }
    server.use(
      http.get(`${API_URL}/v1/workspaces/${workspace.id}/spending-split`, () =>
        HttpResponse.json({
          month: '2026-11',
          fixed: { amount: '2350.00', currency: 'BRL' },
          variable: { amount: '400.00', currency: 'BRL' },
        }),
      ),
    )
    renderWithProviders(<SpendingSplitHomeCard />, { path: '/' })

    expect(await screen.findByText('R$ 2.350,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 400,00')).toBeInTheDocument()
    expect(screen.getByText('Gastos fixos')).toBeInTheDocument()
    expect(screen.getByText('Variáveis')).toBeInTheDocument()
  })
})
