import { cleanup, screen, waitFor } from '@testing-library/react'
import { lazy } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { ModuleSlot } from './ModuleSlot'
import { registerSlot, resetSlots } from './slots'

afterEach(() => {
  cleanup()
  resetSlots()
  vi.restoreAllMocks()
})

const Card = lazy(() => Promise.resolve({ default: () => <p>Card do planejamento</p> }))
const Broken = lazy(() =>
  Promise.resolve({
    default: () => {
      throw new Error('boom')
    },
  }),
)

function setUp(planning: boolean, role: 'OWNER' | 'YOUTH' = 'OWNER') {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { planning }
  mockState.workspaces[0].role = role
  return workspace
}

function renderSlot() {
  return renderWithProviders(
    <>
      <ModuleSlot name="home" />
      <p>resto da página</p>
    </>,
    { path: '/inicio' },
  )
}

describe('ModuleSlot', () => {
  it('shows an entry when the module is on and the role fits', async () => {
    setUp(true)
    registerSlot('home', { key: 'card', module: 'planning', roles: ['OWNER'], Component: Card })
    renderSlot()

    expect(await screen.findByText('Card do planejamento')).toBeInTheDocument()
  })

  it('hides when module is off', async () => {
    const workspace = setUp(false)
    registerSlot('home', { key: 'card', module: 'planning', roles: ['OWNER'], Component: Card })
    const { queryClient } = renderSlot()

    await waitFor(() =>
      expect(queryClient.getQueryState(['ws', workspace.id, 'modules'])?.status).toBe('success'),
    )
    expect(screen.queryByText('Card do planejamento')).not.toBeInTheDocument()
  })

  it('filters by role', async () => {
    const workspace = setUp(true, 'YOUTH')
    registerSlot('home', { key: 'card', module: 'planning', roles: ['OWNER'], Component: Card })
    const { queryClient } = renderSlot()

    await waitFor(() =>
      expect(queryClient.getQueryState(['ws', workspace.id, 'modules'])?.status).toBe('success'),
    )
    expect(screen.queryByText('Card do planejamento')).not.toBeInTheDocument()
  })

  it('a failing entry renders nothing', async () => {
    setUp(true)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    registerSlot('home', { key: 'broken', module: 'planning', roles: ['OWNER'], Component: Broken })
    registerSlot('home', { key: 'card', module: 'planning', roles: ['OWNER'], Component: Card })
    renderSlot()

    expect(await screen.findByText('Card do planejamento')).toBeInTheDocument()
    expect(screen.getByText('resto da página')).toBeInTheDocument()
  })
})
