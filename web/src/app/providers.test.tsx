import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { dehydrate, useQuery } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers'
import { db } from '@/shared/offline'
import { createTestQueryClient } from '@/shared/testing'

afterEach(cleanup)

function Probe({ queryFn }: { queryFn: () => Promise<string> }) {
  const { data } = useQuery({ queryKey: ['probe'], queryFn })
  return <p>{data ?? 'nada'}</p>
}

describe('AppProviders', () => {
  it('fetches again what the device cache restored, even when it is still fresh (I1)', async () => {
    const saved = createTestQueryClient()
    // Saved a moment ago, well inside the 30 s staleTime.
    saved.setQueryData(['probe'], 'salvo')
    await db.kv.put({
      key: 'liberdade-query-cache',
      value: JSON.stringify({ timestamp: Date.now(), buster: '1', clientState: dehydrate(saved) }),
    })
    const queryFn = vi.fn(() => Promise.resolve('do servidor'))

    render(
      <AppProviders queryClient={createTestQueryClient()}>
        <Probe queryFn={queryFn} />
      </AppProviders>,
    )

    expect(await screen.findByText('salvo')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('do servidor')).toBeInTheDocument())
    expect(queryFn).toHaveBeenCalledTimes(1)
  })
})
