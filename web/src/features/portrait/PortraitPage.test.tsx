import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { onlineManager } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type DashboardView } from '@/shared/api'
import type { PlanDetails } from '@/shared/api/billing'
import { createTestQueryClient, renderWithProviders } from '@/shared/testing'

const drawn = vi.hoisted(() => ({ draw: vi.fn() }))
vi.mock('./drawPortrait', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./drawPortrait')>()),
  drawPortrait: drawn.draw,
}))
const saved = vi.hoisted(() => ({ download: vi.fn() }))
vi.mock('@/shared/download', () => ({ download: saved.download }))

import { PortraitPage } from './PortraitPage'

const money = (amount: string) => ({ amount, currency: 'BRL' })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({} as never)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) =>
    callback(new Blob([])),
  )
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  drawn.draw.mockClear()
  saved.download.mockClear()
})

function dashboard(spent: boolean): DashboardView {
  const mercado = mockState.categories.find((category) => category.name === 'Mercado')!.id
  return {
    month: '2026-11',
    income: money('1000.00'),
    expenses: money(spent ? '700.00' : '0.00'),
    result: money(spent ? '300.00' : '1000.00'),
    balance: money('300.00'),
    byCategory: spent
      ? [{ categoryId: mercado, current: money('700.00'), previous: money('0.00') }]
      : [],
    dailyBalance: [],
  }
}

function serve(spent = true, plan?: Partial<PlanDetails>) {
  const calls: string[] = []
  server.use(
    http.get(`${API_URL}/v1/workspaces/:ws/dashboard`, ({ request }) => {
      calls.push(request.url)
      return HttpResponse.json(dashboard(spent))
    }),
  )
  if (plan) {
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/billing/plan`, () =>
        HttpResponse.json({
          key: 'FREE',
          founder: false,
          endsAt: null,
          theme: 'TEAL',
          presaleOpen: true,
          canPurchase: false,
          canRefund: false,
          refundDeadline: null,
          paidOrderId: null,
          openOrder: null,
          ...plan,
        }),
      ),
    )
  }
  return calls
}

const lastOptions = () =>
  drawn.draw.mock.calls.at(-1)![2] as {
    hidden: Set<string>
    founder: boolean
    theme: string | null
  }

function renderPortrait() {
  return renderWithProviders(<PortraitPage />, {
    path: '/painel/retrato',
    route: '/painel/retrato?mes=2026-11',
  })
}

describe('PortraitPage', () => {
  it('helpText', async () => {
    localStorage.setItem('liberdade.tips', 'on')
    seedMockFamily()
    serve(false)
    renderPortrait()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'Uma imagem do mês para compartilhar. Mostra só percentuais, nunca valores.',
    )
  })

  it('disablesShareWhenEmpty', async () => {
    seedMockFamily()
    serve(false)
    renderPortrait()

    expect(await screen.findByText('Ainda não há gastos neste mês')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Compartilhar' })).toBeDisabled()
  })

  it('hidesBlocks', async () => {
    seedMockFamily()
    serve()
    renderPortrait()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Renda' }))

    await waitFor(() => expect(lastOptions().hidden.has('income')).toBe(true))
    expect(lastOptions().hidden.has('categories')).toBe(false)
  })

  it('noNetworkCalls', async () => {
    seedMockFamily()
    const calls = serve()
    renderPortrait()
    await screen.findByRole('img', { name: /Mercado 100%/ })
    await waitFor(() => expect(drawn.draw).toHaveBeenCalled())
    const requests: string[] = []
    server.events.on('request:start', ({ request }) => requests.push(request.url))

    fireEvent.click(screen.getByRole('checkbox', { name: 'Mês' }))
    fireEvent.click(screen.getByRole('button', { name: 'Compartilhar' }))
    await screen.findByText('Retrato salvo no aparelho.')

    expect(saved.download).toHaveBeenCalledWith(expect.any(Blob), 'retrato-2026-11.png')
    expect(requests).toEqual([])
    expect(calls).toHaveLength(1)
    server.events.removeAllListeners()
  })

  it('founderBadge', async () => {
    seedMockFamily()
    serve(true, { key: 'FAMILY', founder: true, theme: 'OCEAN' })
    renderPortrait()

    await waitFor(() => expect(lastOptions().founder).toBe(true))
    expect(lastOptions().theme).toBe('OCEAN')
  })

  it('saysOfflineWhenTheCategoriesWaitForTheSignal', async () => {
    seedMockFamily()
    serve()
    // The categories request fails as the signal drops, so the retry pauses instead of failing.
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/categories`, () => {
        onlineManager.setOnline(false)
        return HttpResponse.error()
      }),
    )
    const queryClient = createTestQueryClient()
    queryClient.setDefaultOptions({
      queries: { ...queryClient.getDefaultOptions().queries, retry: 1, retryDelay: 0 },
    })
    try {
      renderWithProviders(<PortraitPage />, {
        path: '/painel/retrato',
        route: '/painel/retrato?mes=2026-11',
        queryClient,
      })

      expect(
        await screen.findByText('Sem conexão. O Retrato aparece quando o sinal voltar.'),
      ).toBeInTheDocument()
      expect(screen.queryByText('Carregando…')).not.toBeInTheDocument()
    } finally {
      onlineManager.setOnline(true)
    }
  })

  it('drawsAsFreeWhenThePlanFails', async () => {
    seedMockFamily()
    serve()
    let planCalls = 0
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/billing/plan`, () => {
        planCalls += 1
        return HttpResponse.json({ type: 'about:blank', status: 500 }, { status: 500 })
      }),
    )
    const { queryClient } = renderPortrait()

    await screen.findByRole('img', { name: /Mercado 100%/ })
    await waitFor(() => expect(planCalls).toBeGreaterThan(0))
    // The plan query has given up, so the card below is drawn knowing the plan failed.
    await waitFor(() =>
      expect(
        queryClient
          .getQueryCache()
          .getAll()
          .some((query) => query.queryKey.includes('plan') && query.state.status === 'error'),
      ).toBe(true),
    )
    await waitFor(() => expect(drawn.draw).toHaveBeenCalled())
    expect(lastOptions().founder).toBe(false)
    expect(lastOptions().theme).toBeNull()
  })
})
