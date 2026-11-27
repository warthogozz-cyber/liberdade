import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { FAKE_PRODUCTS, mockState, seedMockFamily } from './data'

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

const keep = FAKE_PRODUCTS[0]!.id
const remove = FAKE_PRODUCTS[1]!.id

type Item = { productId: string; quantity: string }

async function listOf(base: string) {
  return ((await call(`${base}/list`)).body.items as Item[]) ?? []
}

describe('product merge mock handler', () => {
  it('movesTheRemovedProductsListItemToTheSurvivor', async () => {
    const base = family()
    await call(`${base}/list`, 'POST', { productId: remove, quantity: '2' })

    const merged = await call(`${base}/products/${keep}/merge`, 'POST', { removeId: remove })

    expect(merged.status).toBe(200)
    const items = await listOf(base)
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({ productId: keep, quantity: '2' })
  })

  it('sumsTheQuantitiesWhenBothProductsAreOnTheList', async () => {
    const base = family()
    await call(`${base}/list`, 'POST', { productId: keep, quantity: '1.5' })
    await call(`${base}/list`, 'POST', { productId: remove, quantity: '2.25' })

    await call(`${base}/products/${keep}/merge`, 'POST', { removeId: remove })

    const items = await listOf(base)
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({ productId: keep, quantity: '3.75' })
  })

  it('answers422WhenTheSumPassesTheLimit', async () => {
    const base = family()
    await call(`${base}/list`, 'POST', { productId: keep, quantity: '999999999999999' })
    await call(`${base}/list`, 'POST', { productId: remove, quantity: '1' })

    const merged = await call(`${base}/products/${keep}/merge`, 'POST', { removeId: remove })

    expect(merged.status).toBe(422)
    expect(merged.body.code).toBe('list.quantity_too_large')
    expect(await listOf(base)).toHaveLength(2)
  })
})
