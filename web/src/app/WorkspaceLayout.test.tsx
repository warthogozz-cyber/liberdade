import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function stubEventSource() {
  const factory = vi.fn(function FakeSource() {
    return { addEventListener: vi.fn(), close: vi.fn(), readyState: 0 }
  })
  vi.stubGlobal('EventSource', factory)
  return factory
}

describe('WorkspaceLayout', () => {
  it('opensTheLiveStreamForAReadyFamily', async () => {
    const workspace = seedMockFamily()
    const factory = stubEventSource()
    renderWithProviders(null, { routes, route: '/lancamentos' })

    await screen.findByRole('navigation', { name: 'Navegação principal' })
    expect(factory).toHaveBeenCalledWith(
      expect.stringContaining(`/workspaces/${workspace.id}/events`),
      { withCredentials: true },
    )
  })

  it('mountsLiveSyncOnlyWhenReady', async () => {
    const workspace = seedMockFamily()
    workspace.deletionScheduledFor = '2026-12-16T12:00:00Z'
    const factory = stubEventSource()
    renderWithProviders(null, { routes, route: '/lancamentos' })

    expect(await screen.findByText(/será excluída/)).toBeInTheDocument()
    expect(factory).not.toHaveBeenCalled()
  })
})
