import { describe, expect, it } from 'vitest'
import { API_URL } from '@/shared/api'
import { mockState, seedMockFamily } from './data'

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

function planningFamily() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], planning: true }
  return workspace
}

const goal = (name: string, amount = '100.00') => ({
  name,
  targetAmount: { amount, currency: 'BRL' },
  icon: 'travel',
})

describe('goal mock handlers', () => {
  it('contributionsReachTheGoal', async () => {
    const workspace = planningFamily()
    const goals = `/v1/workspaces/${workspace.id}/goals`
    const created = await call(goals, 'POST', goal('Viagem'))
    const id = created.body.id as string

    const contribution = await call(`${goals}/${id}/contributions`, 'POST', {
      amount: { amount: '100.00', currency: 'BRL' },
      occurredOn: '2026-11-02',
    })
    const list = await call(goals)

    expect(created.status).toBe(201)
    expect(contribution.status).toBe(201)
    const [view] = list.body.goals as Record<string, unknown>[]
    expect(view.percent).toBe(100)
    expect(view.achievedAt).not.toBeNull()
  })

  it('freePlanStopsAtThreeActiveGoals', async () => {
    const workspace = planningFamily()
    const goals = `/v1/workspaces/${workspace.id}/goals`
    for (const name of ['Um', 'Dois', 'Três']) {
      expect((await call(goals, 'POST', goal(name))).status).toBe(201)
    }

    const fourth = await call(goals, 'POST', goal('Quatro'))
    const list = await call(goals)

    expect(fourth.status).toBe(403)
    expect(fourth.body.code).toBe('goal.limit-reached')
    expect(list.body.limitReached).toBe(true)
  })
})
