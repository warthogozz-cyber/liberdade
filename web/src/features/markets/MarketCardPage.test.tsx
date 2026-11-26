import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import type { MarketCard } from '@/shared/api/markets'
import { renderWithProviders } from '@/shared/testing'
import { MarketCardPage } from './MarketCardPage'
import { distanceMeters, formatDistance } from './geo'
import { writeLocation } from './locationStore'

const REF = 'osm-n-9001'
const FAMILY_ID = '0192f3a4-0000-7000-8000-0000000000c1'
const getCurrentPosition = vi.fn()

beforeEach(() => {
  getCurrentPosition.mockReset()
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition },
    configurable: true,
  })
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.useRealTimers()
})

function seed(role: 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { markets: true }
  return workspace
}

function cardOf(overrides: Partial<MarketCard> = {}): MarketCard {
  return {
    ref: REF,
    familyId: null,
    origin: 'OSM',
    status: 'ACTIVE',
    kind: 'SUPERMARKET',
    name: 'Mercado Exemplo',
    lat: '-10.00012',
    lon: '-50.00034',
    address: { street: 'Rua Exemplo', housenumber: '10', suburb: null, city: null },
    openingHours: 'Mo-Sa 08:00-20:00',
    cnpj: null,
    datasetDate: '2026-01-01',
    website: null,
    instagram: null,
    instagramEmbeddable: false,
    flyer: null,
    updatedAt: null,
    ...overrides,
  }
}

const answerCard = (card: MarketCard) =>
  server.use(http.get(`${API_URL}/v1/workspaces/:ws/markets/:ref`, () => HttpResponse.json(card)))

function render(ref = REF) {
  return renderWithProviders(null, {
    routes: [
      { path: '/mais/feira/mercado/:ref', element: <MarketCardPage /> },
      { path: '/mais/feira', element: <p>Lista da Feira</p> },
    ],
    route: `/mais/feira/mercado/${ref}`,
  })
}

describe('MarketCardPage', () => {
  it('stampsEachLink', async () => {
    seed()
    answerCard(
      cardOf({
        website: { value: 'https://loja.example/', source: 'FAMILY', date: '2026-03-10' },
        instagram: {
          value: 'https://www.instagram.com/exemplo.loja/',
          source: 'OSM',
          date: '2026-01-01',
        },
      }),
    )
    render()

    expect(
      await screen.findByText(/Fonte: adicionado pela família · atualizado em 10\/03\/2026/),
    ).toBeVisible()
    expect(screen.getByText(/Fonte: OpenStreetMap · atualizado em 01\/01\/2026/)).toBeVisible()
  })

  it('expiredFlyer', async () => {
    seed()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-06-15T15:00:00Z'))
    answerCard(
      cardOf({
        flyer: {
          url: 'https://loja.example/encarte',
          source: 'Site da loja',
          validUntil: '2026-06-14',
          setAt: '2026-06-01T12:00:00Z',
        },
      }),
    )
    render()

    expect(await screen.findByText('encarte vencido')).toBeVisible()
  })

  it('flyerValidTodayIsNotExpired', async () => {
    seed()
    vi.useFakeTimers({ toFake: ['Date'] })
    // 01:00 in São Paulo on the 15th (04:00 UTC): the 15th is still the last valid day.
    vi.setSystemTime(new Date('2026-06-15T04:00:00Z'))
    answerCard(
      cardOf({
        flyer: {
          url: 'https://loja.example/encarte',
          source: 'Site da loja',
          validUntil: '2026-06-15',
          setAt: '2026-06-01T12:00:00Z',
        },
      }),
    )
    render()

    expect(await screen.findByText(/Vale até 15\/06\/2026/)).toBeVisible()
    expect(screen.queryByText('encarte vencido')).not.toBeInTheDocument()
  })

  it('externalLinksAreSafe', async () => {
    seed()
    answerCard(
      cardOf({
        website: { value: 'https://loja.example/', source: 'FAMILY', date: '2026-03-10' },
        instagram: {
          value: 'https://www.instagram.com/exemplo.loja/',
          source: 'OSM',
          date: '2026-01-01',
        },
        flyer: {
          url: 'https://loja.example/encarte',
          source: 'Site',
          validUntil: null,
          setAt: '2026-03-10T12:00:00Z',
        },
      }),
    )
    render()

    await screen.findByRole('link', { name: 'Abrir no Maps' })
    const external = screen
      .getAllByRole('link')
      .filter((link) => link.getAttribute('href')?.startsWith('https://'))
    // Maps, directions, Waze, site, Instagram, flyer and the OpenStreetMap credit.
    expect(external.length).toBeGreaterThanOrEqual(7)
    for (const link of external) {
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    }
    expect(screen.getByRole('link', { name: 'Abrir no Waze' })).toHaveAttribute(
      'href',
      'https://waze.com/ul?ll=-10.00012,-50.00034&navigate=yes',
    )
  })

  it('doesNotLinkAValueThatIsNotHttps', async () => {
    seed()
    answerCard(
      cardOf({ website: { value: 'javascript:alert(1)', source: 'OSM', date: '2026-01-01' } }),
    )
    render()

    expect(await screen.findByText('javascript:alert(1)')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'javascript:alert(1)' })).not.toBeInTheDocument()
  })

  it('leftOsmOffersConvertAndArchive', async () => {
    seed('OWNER')
    answerCard(cardOf({ status: 'LEFT_OSM', familyId: FAMILY_ID }))
    render()

    expect(await screen.findByRole('button', { name: 'Passar para a família' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Arquivar' })).toBeVisible()
  })

  it('youthSeesNoWriteControls', async () => {
    seed('YOUTH')
    answerCard(cardOf({ status: 'LEFT_OSM', familyId: FAMILY_ID }))
    render()

    await screen.findByRole('heading', { name: 'Mercado Exemplo' })
    expect(screen.queryByRole('button', { name: 'Passar para a família' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Arquivar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar links' })).not.toBeInTheDocument()
  })

  it('showsNameKindAddressHoursAndDistance', async () => {
    seed()
    writeLocation(mockState.user.id, {
      mode: 'approximate',
      point: { lat: '-10.00', lon: '-50.00' },
    })
    answerCard(cardOf())
    render()

    expect(await screen.findByRole('heading', { name: 'Mercado Exemplo' })).toBeVisible()
    expect(screen.getByText(/Supermercado · Rua Exemplo, 10/)).toBeVisible()
    expect(screen.getByText(/Mo-Sa 08:00-20:00/)).toBeVisible()
    const meters = distanceMeters({ lat: -10, lon: -50 }, { lat: -10.00012, lon: -50.00034 })
    expect(
      screen.getByText(`Supermercado · Rua Exemplo, 10 · ${formatDistance(meters, true)}`),
    ).toBeVisible()
  })

  it('hidesTheDistanceForAPreciseChoice', async () => {
    seed()
    writeLocation(mockState.user.id, { mode: 'precise' })
    answerCard(cardOf())
    render()

    await screen.findByRole('heading', { name: 'Mercado Exemplo' })
    expect(screen.getByText('Supermercado · Rua Exemplo, 10')).toBeVisible()
  })

  it('giveLocationForUnlocatedFamily', async () => {
    seed()
    answerCard(
      cardOf({ ref: FAMILY_ID, familyId: FAMILY_ID, origin: 'FAMILY', lat: null, lon: null }),
    )
    getCurrentPosition.mockImplementation((success: PositionCallback) =>
      success({ coords: { latitude: -10.123456, longitude: -50.654321 } } as GeolocationPosition),
    )
    let body: unknown
    server.use(
      http.put(`${API_URL}/v1/workspaces/:ws/markets/:ref/position`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(cardOf())
      }),
    )
    render(FAMILY_ID)

    fireEvent.click(await screen.findByRole('button', { name: 'Dar a localização' }))
    const dialog = await screen.findByRole('dialog')
    expect(getCurrentPosition).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Estou no mercado agora' }))
    await within(dialog).findByText(/Posição marcada/)
    fireEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(body).toEqual({ lat: '-10.12346', lon: '-50.65432' }))
  })

  it('archiveFamilyMarket', async () => {
    seed()
    answerCard(cardOf({ ref: FAMILY_ID, familyId: FAMILY_ID, origin: 'FAMILY' }))
    let archived = false
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/markets/:ref/archive`, () => {
        archived = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    render(FAMILY_ID)

    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))

    expect(await screen.findByText('Lista da Feira')).toBeVisible()
    expect(archived).toBe(true)
  })
})
