import { onlineManager } from '@tanstack/react-query'
import { cleanup, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedFamilyPlan, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, createQueryClient } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { writeLocation } from './locationStore'
import { MarketsRoutes } from './MarketsRoutes'

afterEach(() => {
  cleanup()
  localStorage.clear()
  onlineManager.setOnline(true)
})

/** A family with the Feira on and a saved approximate position, on the free plan with billing on. */
function seed() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  mockState.billing.plan = { key: 'FREE', endsAt: null }
  writeLocation(mockState.user.id, {
    mode: 'approximate',
    point: { lat: '-10.00', lon: '-50.00' },
  })
  return workspace
}

const render = (queryClient?: ReturnType<typeof createQueryClient>) =>
  renderWithProviders(<MarketsRoutes />, {
    path: '/mais/feira/*',
    route: '/mais/feira',
    queryClient,
  })

describe('MarketsRoutes', () => {
  it('showsThePlanScreenOnTheFreePlan', async () => {
    seed()
    const { router } = render()

    expect(await screen.findByText('Este recurso faz parte do plano Família.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Feira' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver o plano Família' })).toHaveAttribute(
      'href',
      '/mais/plano',
    )
    expect(router.state.location.pathname).toBe('/mais/feira')
  })

  it('opensTheFeiraOnTheFamilyPlan', async () => {
    seed()
    seedFamilyPlan()
    render()

    expect(await screen.findByRole('heading', { level: 1, name: 'Feira' })).toBeInTheDocument()
    expect(screen.queryByText('Este recurso faz parte do plano Família.')).not.toBeInTheDocument()
  })

  it('opensTheFeiraWhileThePlanIsNotOnSale', async () => {
    seed()
    mockState.billing.selling = false
    render()

    expect(await screen.findByRole('heading', { level: 1, name: 'Feira' })).toBeInTheDocument()
    expect(screen.queryByText('Este recurso faz parte do plano Família.')).not.toBeInTheDocument()
  })

  it('opensTheFeiraOfflineWhenThePlanWasNeverRead', async () => {
    const workspace = seed()
    // The one request goes out and fails; with the device offline the retry waits for a connection.
    server.use(
      http.get(`${API_URL}/v1/workspaces/${workspace.id}/billing/plan`, () => {
        onlineManager.setOnline(false)
        return HttpResponse.error()
      }),
    )
    // The app's own client, whose retry rule is what pauses the query offline.
    render(createQueryClient(() => {}))

    // The retry is due after a second; that is when the query pauses and the Feira opens.
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Feira' }, { timeout: 3000 }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Este recurso faz parte do plano Família.')).not.toBeInTheDocument()
  })
})
