import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import type { MarketCard } from '@/shared/api/markets'
import { renderWithProviders } from '@/shared/testing'
import { LinksSheet } from './LinksSheet'

afterEach(cleanup)

function cardOf(overrides: Partial<MarketCard> = {}): MarketCard {
  return {
    ref: 'osm-n-9001',
    familyId: null,
    origin: 'OSM',
    status: 'ACTIVE',
    kind: 'SUPERMARKET',
    name: 'Mercado Exemplo',
    lat: '-10.00012',
    lon: '-50.00034',
    address: null,
    openingHours: null,
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

function open(card: MarketCard) {
  const workspace = seedMockFamily()
  const bodies: unknown[] = []
  server.use(
    http.put(`${API_URL}/v1/workspaces/:ws/markets/:ref/links`, async ({ request }) => {
      bodies.push(await request.json())
      return HttpResponse.json(card)
    }),
  )
  renderWithProviders(
    <LinksSheet
      open
      onOpenChange={() => {}}
      workspaceId={workspace.id}
      marketRef={card.ref}
      card={card}
    />,
  )
  return bodies
}

const field = (name: RegExp) => screen.findByRole('textbox', { name })

describe('LinksSheet', () => {
  it('rejectsHttp', async () => {
    const bodies = open(cardOf())

    fireEvent.change(await field(/Site/), { target: { value: 'http://x.example' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText(/comece com https/)).toBeVisible()
    expect(bodies).toEqual([])
  })

  it('resendsUnchangedFields', async () => {
    const bodies = open(
      cardOf({
        website: { value: 'https://velho.example/', source: 'FAMILY', date: '2026-03-10' },
        // Shown from OpenStreetMap: not the family's, so it must not be copied into the family's row.
        instagram: {
          value: 'https://www.instagram.com/do.osm/',
          source: 'OSM',
          date: '2026-01-01',
        },
        flyer: {
          url: 'https://loja.example/encarte',
          source: 'Site da loja',
          validUntil: '2026-12-31',
          setAt: '2026-03-10T12:00:00Z',
        },
      }),
    )

    fireEvent.change(await field(/Site/), { target: { value: 'https://novo.example/' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({
      website: 'https://novo.example/',
      instagram: null,
      flyerUrl: 'https://loja.example/encarte',
      flyerSource: 'Site da loja',
      flyerValidUntil: '2026-12-31',
    })
  })

  it('sendsNullForWhatTheFamilyNeverSet', async () => {
    const bodies = open(cardOf())

    fireEvent.change(await field(/Instagram/), { target: { value: '@loja.exemplo' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({
      website: null,
      instagram: '@loja.exemplo',
      flyerUrl: null,
      flyerSource: null,
      flyerValidUntil: null,
    })
  })

  it('showsTheApiErrorWhenTheDeviceLetItThrough', async () => {
    server.use(
      http.put(`${API_URL}/v1/workspaces/:ws/markets/:ref/links`, () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Unprocessable',
            status: 422,
            code: 'markets.link_invalid',
            detail: 'Esse endereço não vale.',
          },
          { status: 422 },
        ),
      ),
    )
    const workspace = seedMockFamily()
    renderWithProviders(
      <LinksSheet
        open
        onOpenChange={() => {}}
        workspaceId={workspace.id}
        marketRef="osm-n-9001"
        card={cardOf()}
      />,
    )

    fireEvent.change(await field(/Site/), { target: { value: 'https://x.example/' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Esse endereço não vale.')).toBeVisible()
  })

  it('needsASourceForTheFlyer', async () => {
    const bodies = open(cardOf())

    fireEvent.change(await field(/Encarte \(https/), {
      target: { value: 'https://loja.example/encarte' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText(/Diga de onde veio o encarte/)).toBeVisible()
    expect(bodies).toEqual([])
  })
})
