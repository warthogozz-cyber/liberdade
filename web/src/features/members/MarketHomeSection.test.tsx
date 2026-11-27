import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { MarketHomeSection } from './MarketHomeSection'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function seed(role: 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { markets: true }
  return workspace
}

const renderSection = () => renderWithProviders(<MarketHomeSection />)

describe('MarketHomeSection', () => {
  it('storesOnlyPointAndLabel', async () => {
    seed()
    let body: unknown
    server.use(
      http.put(`${API_URL}/v1/workspaces/:ws/markets/home`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(body as Record<string, unknown>)
      }),
    )
    renderSection()

    fireEvent.change(await screen.findByLabelText('Endereço'), {
      target: { value: 'Rua Exemplo 10, Cidade Exemplo' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    fireEvent.click(await screen.findByRole('radio', { name: 'Bairro Exemplo, Cidade Exemplo' }))
    const label = screen.getByLabelText('Rótulo (opcional)')
    expect(label).toHaveValue('Bairro Exemplo, Cidade Exemplo')
    fireEvent.change(label, { target: { value: 'Casa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar endereço' }))

    await waitFor(() => expect(body).toEqual({ lat: '-10.000', lon: '-50.000', label: 'Casa' }))
    expect(JSON.stringify(body)).not.toContain('Rua Exemplo')
    // The typed text never reaches the device's storage.
    expect(JSON.stringify({ ...localStorage })).not.toContain('Rua Exemplo')
    expect(screen.getByText('Busca de endereço © OpenStreetMap')).toBeVisible()
    expect(
      screen.getByText(
        'Serve para achar mercados perto de casa. Guardamos só um ponto aproximado e o rótulo.',
      ),
    ).toBeVisible()
  })

  it('hiddenForYouth', async () => {
    for (const role of ['YOUTH', 'VIEWER'] as const) {
      seed(role)
      const { unmount } = renderSection()
      await new Promise((resolve) => setTimeout(resolve, 30))
      expect(screen.queryByText('Endereço da família na Feira')).not.toBeInTheDocument()
      unmount()
    }
  })

  it('geocodingOffCannotChange', async () => {
    seed()
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/markets/settings`, () =>
        HttpResponse.json({ geocodingEnabled: false, instagramEnabled: true }),
      ),
      http.get(`${API_URL}/v1/workspaces/:ws/markets/home`, () =>
        HttpResponse.json({ lat: '-10.000', lon: '-50.000', label: 'Casa' }),
      ),
    )
    renderSection()

    expect(await screen.findByText('Endereço atual: Casa')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Remover' })).toBeVisible()
    expect(screen.queryByLabelText('Endereço')).not.toBeInTheDocument()
  })

  it('removeAsksFirst', async () => {
    seed()
    let removed = false
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/markets/home`, () =>
        removed
          ? new HttpResponse(null, { status: 204 })
          : HttpResponse.json({ lat: '-10.000', lon: '-50.000', label: 'Casa' }),
      ),
      http.delete(`${API_URL}/v1/workspaces/:ws/markets/home`, () => {
        removed = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderSection()

    fireEvent.click(await screen.findByRole('button', { name: 'Remover' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(removed).toBe(false)
    fireEvent.click(within(dialog).getByRole('button', { name: 'Remover' }))

    expect(await screen.findByText('Nenhum endereço salvo.')).toBeVisible()
    expect(removed).toBe(true)
  })
})
