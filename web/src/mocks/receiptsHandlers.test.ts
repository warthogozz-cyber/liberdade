import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { FAKE_OSM_MARKETS, mockState, seedMockFamily } from './data'

// The mock checks only the length and the state code: lint keeps mocks from importing the feature's key generator.
const makeChave = (options: { uf?: string; aamm?: string; number?: number } = {}) =>
  `${options.uf ?? '43'}${options.aamm ?? '2611'}${String(options.number ?? 1).padStart(38, '0')}`

const WRITE = { 'X-XSRF-TOKEN': 'mock-csrf', 'Content-Type': 'application/json' }

async function call(path: string, method = 'GET', body?: unknown) {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: method === 'GET' ? {} : WRITE,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  return {
    status: response.status,
    body: (text ? JSON.parse(text) : null) as Record<string, unknown>,
  }
}

function family() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], markets: true }
  return `/v1/workspaces/${workspace.id}/markets`
}

describe('receipt mock handlers', () => {
  it('looksUpAsManualWithoutAMarket', async () => {
    const base = family()
    const chave = makeChave({ uf: '43', aamm: '2611' })
    const found = await call(`${base}/receipts/lookup`, 'POST', { chave, version: 0 })
    expect(found.status).toBe(200)
    expect(found.body).toMatchObject({
      mode: 'MANUAL',
      uf: 'RS',
      issuedMonth: '2026-11',
      market: null,
      items: [],
    })
  })

  it('refusesAShortKey', async () => {
    const base = family()
    const found = await call(`${base}/receipts/lookup`, 'POST', { chave: '123', version: 0 })
    expect(found.status).toBe(422)
    expect(found.body.code).toBe('receipt.key_invalid')
  })

  it('savesAReceiptThenListsItsPricesAndProducts', async () => {
    const base = family()
    const chave = makeChave({ number: 7 })
    const ref = FAKE_OSM_MARKETS[0]!.ref
    const saved = await call(`${base}/receipts`, 'POST', {
      chave,
      version: 0,
      issuedOn: '2026-11-10',
      market: { ref },
      newMarket: null,
      items: [{ newProductName: 'Café torrado', unit: 'PCT', quantity: '2', unitPrice: '12.5' }],
    })
    expect(saved.status).toBe(201)
    expect(saved.body).toMatchObject({ marketRef: ref, prices: 1 })

    const again = await call(`${base}/receipts`, 'POST', {
      chave,
      version: 0,
      issuedOn: '2026-11-10',
      market: { ref },
      items: [],
    })
    expect(again.status).toBe(409)
    expect(again.body.code).toBe('receipt.duplicate')

    const prices = await call(`${base}/${ref}/prices`)
    expect(prices.status).toBe(200)
    const list = prices.body.prices as { name: string; unitPrice: string; observedOn: string }[]
    expect(list.find((each) => each.name === 'Café torrado')).toMatchObject({
      unitPrice: '12.5000',
      observedOn: '2026-11-10',
    })

    const products = await call(`${base}/products?q=caf`)
    expect((products.body.products as { name: string }[]).map((each) => each.name)).toEqual([
      'Café torrado',
    ])
  })

  it('needsExactlyOneMarket', async () => {
    const base = family()
    const body = { chave: makeChave({ number: 8 }), version: 0, issuedOn: '2026-11-10', items: [] }
    const none = await call(`${base}/receipts`, 'POST', body)
    expect(none.status).toBe(422)
    expect(none.body.code).toBe('receipt.market_required')
  })

  it('listsInventedProductsAndPricesFromTheStart', async () => {
    const base = family()
    const products = await call(`${base}/products`)
    expect((products.body.products as unknown[]).length).toBeGreaterThan(0)
    const prices = await call(`${base}/${FAKE_OSM_MARKETS[0]!.ref}/prices`)
    expect((prices.body.prices as unknown[]).length).toBeGreaterThan(0)
    const unknown = await call(`${base}/osm-n-1/prices`)
    expect(unknown.status).toBe(404)
  })
})
