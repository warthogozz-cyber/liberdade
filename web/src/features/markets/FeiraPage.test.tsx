import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { FeiraPage } from './FeiraPage'
import { writeLocation } from './locationStore'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

/** A family with the module on and a saved approximate position at (-10.00, -50.00). */
function seed(role: 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { markets: true }
  writeLocation(mockState.user.id, {
    mode: 'approximate',
    point: { lat: '-10.00', lon: '-50.00' },
  })
  return workspace
}

const render = () => renderWithProviders(<FeiraPage />, { path: '/mais/feira' })
const marketLinks = () =>
  screen
    .getAllByRole('link')
    .filter((link) => link.getAttribute('href')?.startsWith('/mais/feira/mercado/'))

function osm(index: number, lat: number, name: string | null = `Mercado ${index}`) {
  return {
    ref: `osm-n-${index}`,
    name,
    kind: 'SUPERMARKET',
    lat: lat.toFixed(5),
    lon: '-50.00000',
    street: null,
    housenumber: null,
    suburb: null,
    city: null,
    brand: null,
    familyId: null,
  }
}

const answerSearch = (items: ReturnType<typeof osm>[]) =>
  server.use(
    http.post(`${API_URL}/v1/workspaces/:ws/markets/search`, () =>
      HttpResponse.json({
        datasetDate: '2026-01-01',
        osm: items,
        family: [],
        left: [],
        unlocated: [],
      }),
    ),
  )

describe('FeiraPage', () => {
  it('sortsByDistanceAndCapsAtFifty', async () => {
    seed()
    // Sixty markets, the farthest listed first; the 0.0003 degree step is about 33 m.
    answerSearch(
      Array.from({ length: 60 }, (_, index) => osm(59 - index, -10 + (59 - index) * 0.0003)),
    )
    render()

    await screen.findByText('Mercado 0')
    const links = marketLinks()
    expect(links).toHaveLength(50)
    expect(links[0]).toHaveTextContent('Mercado 0')
    expect(links[49]).toHaveTextContent('Mercado 49')
  })

  it('dropsWhatIsOutsideTheRadius', async () => {
    seed()
    // About 5.5 km north: inside the answer the server gave, outside the 3 km radius.
    answerSearch([osm(1, -10.0), osm(2, -9.95, 'Mercado Longe')])
    render()

    expect(await screen.findByText('Mercado 1')).toBeVisible()
    expect(screen.queryByText('Mercado Longe')).not.toBeInTheDocument()
  })

  it('attributionAlwaysVisible', async () => {
    seed()
    render()

    const link = await screen.findByRole('link', { name: /OpenStreetMap/ })
    expect(link).toHaveAttribute('href', 'https://www.openstreetmap.org/copyright')
    expect(link).toBeVisible()
  })

  it('unnamedShowsKind', async () => {
    seed()
    answerSearch([osm(1, -10.0, null)])
    render()

    expect(await screen.findByText('Supermercado sem nome')).toBeVisible()
  })

  it('showsTheFakeDatasetByDefault', async () => {
    seed()
    render()

    expect(await screen.findByText('Mercado Exemplo 3')).toBeVisible()
    expect(marketLinks().length).toBeGreaterThan(1)
  })

  it('readerHasNoAddButton', async () => {
    seed('VIEWER')
    render()

    await screen.findByText('Mercado Exemplo 3')
    expect(screen.queryByRole('button', { name: /Adicionar mercado/ })).not.toBeInTheDocument()
  })

  it.each(['OWNER', 'ADULT'] as const)('%s has the add button', async (role) => {
    seed(role)
    render()

    expect(await screen.findByRole('button', { name: /Adicionar mercado/ })).toBeVisible()
  })

  it('youthMakesNoHomeRequestAndHasNoFamilyAddress', async () => {
    seed('YOUTH')
    const paths: string[] = []
    const record = ({ request }: { request: Request }) => paths.push(new URL(request.url).pathname)
    server.events.on('request:start', record)
    render()

    await screen.findByText('Mercado Exemplo 3')
    fireEvent.click(screen.getByRole('button', { name: /Onde procurar|Aproximada|Escolher/i }))
    expect(await screen.findByRole('radio', { name: /Minha localização precisa/ })).toBeVisible()
    expect(screen.queryByRole('radio', { name: /Endereço da família/ })).not.toBeInTheDocument()
    server.events.removeListener('request:start', record)
    expect(paths.filter((path) => path.endsWith('/markets/home'))).toEqual([])
    expect(screen.queryByRole('button', { name: /Adicionar mercado/ })).not.toBeInTheDocument()
  })

  it('asksWhereToLookTheFirstTime', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { markets: true }
    const paths: string[] = []
    const record = ({ request }: { request: Request }) => paths.push(new URL(request.url).pathname)
    server.events.on('request:start', record)
    render()

    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByRole('radio', { name: /Minha localização aproximada/ }),
    ).toBeVisible()
    await waitFor(() => expect(paths.some((path) => path.endsWith('/markets/settings'))).toBe(true))
    server.events.removeListener('request:start', record)
    expect(paths.filter((path) => path.endsWith('/markets/search'))).toEqual([])
  })

  it('listsTheUnlocatedMarketsAtTheEnd', async () => {
    const workspace = seed()
    mockState.markets[workspace.id] = {
      family: [
        {
          id: '0192f3a4-0000-7000-8000-0000000000b1',
          name: 'Feira do bairro',
          kind: 'MARKETPLACE',
          lat: null,
          lon: null,
          archived: false,
          instagram: null,
          website: null,
        },
      ],
      home: null,
      osmLinks: {},
      archivedOsm: [],
    }
    render()

    expect(await screen.findByText('Seus mercados sem localização')).toBeVisible()
    expect(screen.getByText('Feira do bairro')).toBeVisible()
  })

  it('showsAnErrorWithRetry', async () => {
    seed()
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/markets/search`, () =>
        HttpResponse.json({ status: 500 }, { status: 500 }),
      ),
    )
    render()

    expect(await screen.findByText(/Não deu para carregar/)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeVisible()
  })
})
