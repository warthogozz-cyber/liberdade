import { cleanup, render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { emitLocalAction } from '@/shared/events'
import { createTestQueryClient } from '@/shared/testing'
import PlanningInvalidator from './PlanningInvalidator'

afterEach(cleanup)

describe('PlanningInvalidator', () => {
  it('invalidates on any local action', () => {
    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const { unmount } = render(
      <QueryClientProvider client={queryClient}>
        <PlanningInvalidator />
      </QueryClientProvider>,
    )

    emitLocalAction({ type: 'transaction-saved', workspaceId: 'ws-1', fromQueue: true })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['ws', 'ws-1', 'planning'] })

    unmount()
    emitLocalAction({ type: 'contribution-saved', workspaceId: 'ws-1', fromQueue: false })
    expect(invalidate).toHaveBeenCalledTimes(1)
  })
})
