import { act, cleanup, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { prdExampleFigures, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderHookWithProviders } from '@/shared/testing'
import { useVentureSimulation, type VentureDraft } from './useVentureSimulation'

const URL = `${API_URL}/v1/workspaces/:ws/ventures/simulation`

function draft(units: string, overrides: Partial<VentureDraft> = {}): VentureDraft {
  return {
    name: 'Doces',
    type: 'FOOD',
    startedOn: null,
    initialItems: [{ key: 'i1', id: null, name: 'Forma', amount: '600,00', isExample: false }],
    fixedItems: [{ key: 'f1', id: null, name: 'Gás', amount: '60,00', isExample: false }],
    products: [
      {
        key: 'p1',
        id: null,
        name: 'Brigadeiro',
        unitPrice: '4,50',
        unitVariableCost: '1,80',
        plannedUnits: units,
        isExample: false,
      },
    ],
    ...overrides,
  }
}

function setup() {
  const workspace = seedMockFamily()
  seedVentureFixture(workspace)
  return workspace.id
}

afterEach(() => {
  cleanup()
})

// Real timers: fake ones starve MSW and `waitFor`. The margins are wide enough not to flake.
async function advance(ms: number) {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms))
  })
}

describe('useVentureSimulation', () => {
  it('waits400ms', async () => {
    const ws = setup()
    const bodies: unknown[] = []
    const arrivedAt: number[] = []
    server.use(
      http.post(URL, async ({ request }) => {
        arrivedAt.push(performance.now())
        bodies.push(await request.json())
        return HttpResponse.json(prdExampleFigures())
      }),
    )
    let current = draft('10')
    const { rerender } = renderHookWithProviders(() => useVentureSimulation(ws, current))
    await advance(50)
    await waitFor(() => expect(bodies).toHaveLength(1))
    bodies.length = 0
    arrivedAt.length = 0

    let lastChange = 0
    for (const units of ['20', '30', '40']) {
      current = draft(units)
      rerender()
      lastChange = performance.now()
      await advance(100)
    }

    await waitFor(() => expect(bodies).toHaveLength(1), { timeout: 3000 })
    // Timers never fire early, so this holds on a slow machine too: only a shorter delay fails it.
    expect(arrivedAt[0]! - lastChange).toBeGreaterThanOrEqual(390)
    expect(bodies[0]).toEqual({
      initialItems: [{ amount: '600.00' }],
      fixedItems: [{ amount: '60.00' }],
      products: [{ unitPrice: '4.50', unitVariableCost: '1.80', plannedUnitsMonth: 40 }],
    })
  })

  it('typingANameAsksForNothing', async () => {
    const ws = setup()
    let calls = 0
    server.use(
      http.post(URL, () => {
        calls += 1
        return HttpResponse.json(prdExampleFigures())
      }),
    )
    let current = draft('10')
    const { rerender } = renderHookWithProviders(() => useVentureSimulation(ws, current))
    await advance(50)
    await waitFor(() => expect(calls).toBe(1))

    current = draft('10', { name: 'Outro nome' })
    rerender()
    await advance(1000)

    expect(calls).toBe(1)
  })

  it('lastRequestWins', async () => {
    const ws = setup()
    const slow = { ...prdExampleFigures(), monthlyProfit: { amount: '1.00', currency: 'BRL' } }
    const fast = { ...prdExampleFigures(), monthlyProfit: { amount: '2.00', currency: 'BRL' } }
    server.use(
      http.post(URL, async ({ request }) => {
        const body = (await request.json()) as { products: { plannedUnitsMonth: number }[] }
        if (body.products[0]?.plannedUnitsMonth === 20) {
          await new Promise((resolve) => setTimeout(resolve, 1000))
          return HttpResponse.json(slow)
        }
        return HttpResponse.json(fast)
      }),
    )
    let current = draft('20')
    const { result, rerender } = renderHookWithProviders(() => useVentureSimulation(ws, current))
    // The first draft (20 units) is the slow one, still on its way when the next one is typed.
    await advance(100)
    current = draft('30')
    rerender()
    await advance(400)
    await waitFor(() => expect(result.current.figures?.monthlyProfit.amount).toBe('2.00'))

    await advance(2000)

    expect(result.current.figures?.monthlyProfit.amount).toBe('2.00')
  })

  it('skipsRowsThatDoNotParse', async () => {
    const ws = setup()
    const bodies: unknown[] = []
    server.use(
      http.post(URL, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(prdExampleFigures())
      }),
    )
    const broken = draft('10', {
      fixedItems: [
        { key: 'f1', id: null, name: 'Gás', amount: '12,3,4', isExample: false },
        { key: 'f2', id: null, name: 'Luz', amount: '30,00', isExample: false },
      ],
    })
    const { result } = renderHookWithProviders(() => useVentureSimulation(ws, broken))
    await advance(50)

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect((bodies[0] as { fixedItems: unknown }).fixedItems).toEqual([{ amount: '30.00' }])
    await waitFor(() => expect(result.current.figures).toBeDefined())
  })
})
