import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { ConvertMarketSheet } from './ConvertMarketSheet'

const getCurrentPosition = vi.fn()
const REF = '0192f3a4-0000-7000-8000-0000000000c1'

beforeEach(() => {
  getCurrentPosition.mockReset()
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition },
    configurable: true,
  })
})

afterEach(cleanup)

function open() {
  const workspace = seedMockFamily()
  renderWithProviders(
    <ConvertMarketSheet
      open
      onOpenChange={() => {}}
      workspaceId={workspace.id}
      marketRef={REF}
      geocodingEnabled={false}
      onConverted={() => {}}
    />,
  )
}

describe('ConvertMarketSheet', () => {
  it('sendsTypedNameOnly', async () => {
    getCurrentPosition.mockImplementation((success: PositionCallback) =>
      success({ coords: { latitude: -10.5, longitude: -50.25 } } as GeolocationPosition),
    )
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/markets/:ref/convert`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ ref: 'x', familyId: 'x' })
      }),
    )
    open()

    const name = await screen.findByRole('textbox', { name: 'Nome do mercado' })
    // Nothing from the map is put in the field.
    expect(name).toHaveValue('')
    fireEvent.change(name, { target: { value: 'Mercado Novo' } })
    fireEvent.change(screen.getByRole('combobox', { name: 'Tipo' }), {
      target: { value: 'GREENGROCER' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Estou no mercado agora' }))
    await screen.findByText(/Posição marcada/)
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({
      name: 'Mercado Novo',
      kind: 'GREENGROCER',
      lat: '-10.50000',
      lon: '-50.25000',
    })
  })

  it('needsAPosition', async () => {
    open()

    fireEvent.change(await screen.findByRole('textbox', { name: 'Nome do mercado' }), {
      target: { value: 'Mercado Novo' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Marque a posição do mercado.')).toBeVisible()
  })
})
