import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { MarketPrices } from './MarketPrices'

afterEach(cleanup)

const PRICES = `${API_URL}/v1/workspaces/:ws/markets/:ref/prices`

function render(canRegister: boolean) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  renderWithProviders(
    <MarketPrices ws={workspace.id} marketRef="osm-n-9001" canRegister={canRegister} />,
    { path: '/mais/feira/mercado/:ref', route: '/mais/feira/mercado/osm-n-9001' },
  )
}

describe('MarketPrices', () => {
  it('showsPriceAndDate', async () => {
    server.use(
      http.get(PRICES, () =>
        HttpResponse.json({
          prices: [
            {
              productId: '00000000-0000-4000-8000-00000000a001',
              name: 'Arroz tipo 1',
              unit: 'KG',
              unitPrice: '4.9900',
              observedOn: '2026-11-12',
            },
          ],
        }),
      ),
    )
    render(true)

    expect(await screen.findByText('Arroz tipo 1')).toBeVisible()
    expect(screen.getByText('R$ 4,99 · 12/11/2026')).toBeVisible()
    expect(screen.getByText('quilo')).toBeVisible()
  })

  it('roundsHalfUpToCents', async () => {
    server.use(
      http.get(PRICES, () =>
        HttpResponse.json({
          prices: [
            {
              productId: '00000000-0000-4000-8000-00000000a001',
              name: 'Arroz tipo 1',
              unit: 'KG',
              unitPrice: '4.9950',
              observedOn: '2026-11-12',
            },
          ],
        }),
      ),
    )
    render(false)

    expect(await screen.findByText('R$ 5,00 · 12/11/2026')).toBeVisible()
  })

  it('emptyState', async () => {
    server.use(http.get(PRICES, () => HttpResponse.json({ prices: [] })))
    render(true)

    expect(await screen.findByText('Nenhum preço registrado ainda.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Registrar nota' })).toHaveAttribute(
      'href',
      '/mais/feira/nota',
    )
  })

  it('emptyStateHasNoButtonForYouth', async () => {
    server.use(http.get(PRICES, () => HttpResponse.json({ prices: [] })))
    render(false)

    expect(await screen.findByText('Nenhum preço registrado ainda.')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Registrar nota' })).not.toBeInTheDocument()
  })
})
