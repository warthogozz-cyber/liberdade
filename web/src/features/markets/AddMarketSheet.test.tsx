import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { AddMarketSheet } from './AddMarketSheet'

const getCurrentPosition = vi.fn()

beforeEach(() => {
  getCurrentPosition.mockReset()
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition },
    configurable: true,
  })
})

afterEach(cleanup)

function open(geocodingEnabled = true) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  const bodies: unknown[] = []
  server.use(
    http.post(`${API_URL}/v1/workspaces/:ws/markets`, async ({ request }) => {
      bodies.push(await request.json())
      return HttpResponse.json({ ref: 'x', familyId: 'x' }, { status: 201 })
    }),
  )
  const onCreated = vi.fn()
  renderWithProviders(
    <AddMarketSheet
      open
      onOpenChange={() => {}}
      workspaceId={workspace.id}
      geocodingEnabled={geocodingEnabled}
      onCreated={onCreated}
    />,
  )
  return { bodies, onCreated }
}

describe('AddMarketSheet', () => {
  it('positionOnlyOnTap', async () => {
    getCurrentPosition.mockImplementation((success: PositionCallback) =>
      success({ coords: { latitude: -10.123456, longitude: -50.654321 } } as GeolocationPosition),
    )
    const { bodies, onCreated } = open()

    const here = await screen.findByRole('button', { name: 'Estou no mercado agora' })
    expect(getCurrentPosition).not.toHaveBeenCalled()
    fireEvent.change(screen.getByRole('textbox', { name: 'Nome do mercado' }), {
      target: { value: 'Mercado do Bairro' },
    })
    fireEvent.click(here)

    await screen.findByText(/Posição marcada/)
    expect(getCurrentPosition).toHaveBeenCalledTimes(1)
    expect(getCurrentPosition.mock.calls[0]?.[2]).toMatchObject({ enableHighAccuracy: true })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({
      name: 'Mercado do Bairro',
      kind: 'SUPERMARKET',
      lat: '-10.12346',
      lon: '-50.65432',
    })
    await waitFor(() => expect(onCreated).toHaveBeenCalled())
  })

  it('looksAnAddressUpAndListsThePlaces', async () => {
    const { bodies } = open()

    fireEvent.click(await screen.findByRole('button', { name: 'Buscar endereço' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Endereço do mercado' }), {
      target: { value: 'Rua Exemplo 10' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    const places = await screen.findAllByRole('radio')
    fireEvent.click(places[0] as HTMLElement)
    fireEvent.change(screen.getByRole('textbox', { name: 'Nome do mercado' }), {
      target: { value: 'Mercado do Bairro' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ name: 'Mercado do Bairro' })
    expect((bodies[0] as { lat?: string }).lat).toBeDefined()
    expect(getCurrentPosition).not.toHaveBeenCalled()
  })

  it('hidesTheAddressSearchWhenItIsOff', async () => {
    open(false)

    await screen.findByRole('button', { name: 'Estou no mercado agora' })
    expect(screen.queryByRole('button', { name: 'Buscar endereço' })).not.toBeInTheDocument()
  })

  it('pointsToOpenStreetMapForMissingPlaces', async () => {
    open()

    const link = await screen.findByRole('link', { name: 'Cadastrar no OpenStreetMap' })
    expect(link).toHaveAttribute('href', 'https://www.openstreetmap.org/fixthemap')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('aTimeoutBringsTheButtonBack', async () => {
    getCurrentPosition.mockImplementation(
      (_success: PositionCallback, failure: PositionErrorCallback) =>
        failure({ code: 3 } as GeolocationPositionError),
    )
    open()

    fireEvent.click(await screen.findByRole('button', { name: 'Estou no mercado agora' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/Não deu para obter/)
    expect(screen.getByRole('button', { name: 'Estou no mercado agora' })).toBeEnabled()
    expect(getCurrentPosition.mock.calls[0]?.[2]).toMatchObject({ timeout: 15000 })
  })
})
