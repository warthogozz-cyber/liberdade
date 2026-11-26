import { cleanup, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { writeLocation } from '@/features/markets/locationStore'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

describe('MarketsRoutes', () => {
  it('opensTheFeiraWhenTheModuleIsOn', async () => {
    const workspace = seedMockFamily()
    mockState.modules[workspace.id] = { markets: true }
    writeLocation(mockState.user.id, {
      mode: 'approximate',
      point: { lat: '-10.00', lon: '-50.00' },
    })

    renderWithProviders(null, { routes, route: '/mais/feira' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Feira' })).toBeInTheDocument()
    // Let the search finish, so it does not land in the next test.
    expect(await screen.findByText('Mercado Exemplo 3')).toBeVisible()
  })

  it('makesNoMarketsRequestWhileTheModuleIsOff', async () => {
    seedMockFamily()
    const paths: string[] = []
    const record = ({ request }: { request: Request }) => paths.push(new URL(request.url).pathname)
    server.events.on('request:start', record)

    const { router } = renderWithProviders(null, { routes, route: '/mais/feira' })

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    server.events.removeListener('request:start', record)
    expect(paths.filter((path) => path.includes('/markets'))).toEqual([])
  })
})
