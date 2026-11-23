import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { dehydrate, useQuery } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { useIntl } from 'react-intl'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '@/app/providers'
import { mockState } from '@/mocks/data'
import { server } from '@/mocks/server'
import { queryKeys } from '@/shared/api'
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

  it('does not request /v1/me by itself, so a signed-out visitor stays on a public page', async () => {
    mockState.signedIn = false
    let requests = 0
    server.use(
      http.get('*/v1/me', () => {
        requests += 1
        return HttpResponse.json({}, { status: 401 })
      }),
    )
    render(
      <AppProviders queryClient={createTestQueryClient()}>
        <p>publica</p>
      </AppProviders>,
    )

    expect(await screen.findByText('publica')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 150))
    expect(requests).toBe(0)
  })

  it('writes in the locale of the family already cached', async () => {
    const queryClient = createTestQueryClient()
    function Locale() {
      return <p>{useIntl().locale}</p>
    }
    render(
      <AppProviders queryClient={queryClient}>
        <Locale />
      </AppProviders>,
    )
    expect(await screen.findByText('pt-BR')).toBeInTheDocument()
    queryClient.setQueryData(queryKeys.me, {
      user: { id: 'u', displayName: 'Ana' },
      workspaces: [{ id: 'w', locale: 'en-US', currency: 'USD', deletionScheduledFor: null }],
    })
    expect(await screen.findByText('en-US')).toBeInTheDocument()
  })
})
