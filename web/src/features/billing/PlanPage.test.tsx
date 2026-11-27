import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import type { OrderView, PlanDetails } from '@/shared/api/billing'
import { renderWithProviders } from '@/shared/testing'
import { legalConfig } from '@/features/legal/legalConfig'
import { PlanPage } from './PlanPage'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

const FREE: PlanDetails = {
  key: 'FREE',
  founder: false,
  endsAt: null,
  theme: 'TEAL',
  presaleOpen: true,
  canPurchase: true,
  canRefund: false,
  importAllowed: false,
  refundDeadline: null,
  paidOrderId: null,
  openOrder: null,
}

const FOUNDER: PlanDetails = {
  ...FREE,
  key: 'FAMILY',
  founder: true,
  endsAt: '2027-11-17T15:00:00Z',
  canPurchase: false,
  canRefund: true,
  importAllowed: true,
  refundDeadline: '2026-11-25T03:00:00Z',
  paidOrderId: 'o1',
}

function plan(ws: string, view: PlanDetails | (() => PlanDetails)) {
  server.use(
    http.get(`${API_URL}/v1/workspaces/${ws}/billing/plan`, () =>
      HttpResponse.json(typeof view === 'function' ? view() : view),
    ),
  )
}

function renderPlan(route = '/mais/plano', assign?: (url: string) => void) {
  return renderWithProviders(<PlanPage assign={assign} />, { path: '/mais/plano', route })
}

describe('PlanPage', () => {
  it('ownerFreeSeesOffer', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FREE)
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/checkout`, () =>
        HttpResponse.json({ orderId: 'o1', checkoutUrl: 'https://pay.test/c/1' }, { status: 201 }),
      ),
    )
    const assign = vi.fn()
    renderPlan('/mais/plano', assign)

    expect(await screen.findByText('Seu plano: Grátis')).toBeInTheDocument()
    expect(screen.getByText(/R\$ 89,00/)).toBeInTheDocument()
    expect(screen.getByText('Hoje inclui: tema e selo.')).toBeInTheDocument()
    expect(
      screen.getByText('Pix ou cartão, na página segura do Asaas. Reembolso em 7 dias.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Ser Família Fundadora' }))

    await waitFor(() => expect(assign).toHaveBeenCalledWith('https://pay.test/c/1'))
  })

  it('presaleClosedShowsOnlyFree', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, { ...FREE, presaleOpen: false, canPurchase: false })
    renderPlan()

    expect(await screen.findByText(/continua grátis/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('nonOwnerSeesWhoManages', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'ADULT'
    plan(workspace.id, { ...FREE, canPurchase: false })
    renderPlan()

    expect(await screen.findByText('Quem cuida do plano é o Dono da família.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('returnPollsUntilPaid', async () => {
    const workspace = seedMockFamily()
    let planCalls = 0
    let refreshCalls = 0
    let paid = false
    plan(workspace.id, () => {
      planCalls += 1
      return paid
        ? FOUNDER
        : { ...FREE, openOrder: { id: 'o1', status: 'CREATED', refundReason: null } }
    })
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refresh`, () => {
        refreshCalls += 1
        paid = refreshCalls > 2
        const view: OrderView = {
          id: 'o1',
          status: paid ? 'PAID' : 'CREATED',
          refundReason: null,
        }
        return HttpResponse.json(view)
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderPlan('/mais/plano?pedido=o1')

    expect(await screen.findByText(/Esperando a confirmação do pagamento/)).toBeInTheDocument()
    await waitFor(() => expect(refreshCalls).toBe(1))
    await act(() => vi.advanceTimersByTimeAsync(5000))
    await waitFor(() => expect(refreshCalls).toBe(2))
    await act(() => vi.advanceTimersByTimeAsync(5000))

    expect(await screen.findByText('★ Família Fundadora')).toBeInTheDocument()
    expect(screen.queryByText(/Esperando a confirmação/)).not.toBeInTheDocument()
    expect(planCalls).toBeGreaterThanOrEqual(2)
    const settled = refreshCalls
    await act(() => vi.advanceTimersByTimeAsync(20_000))
    expect(refreshCalls).toBe(settled)
  })

  it('pollingStopsAfterTwoMinutes', async () => {
    const workspace = seedMockFamily()
    let refreshCalls = 0
    let planCalls = 0
    plan(workspace.id, () => {
      planCalls += 1
      return { ...FREE, openOrder: { id: 'o1', status: 'CREATED', refundReason: null } }
    })
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refresh`, () => {
        refreshCalls += 1
        return HttpResponse.json({ id: 'o1', status: 'CREATED', refundReason: null })
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderPlan('/mais/plano?pedido=o1')

    expect(await screen.findByRole('status')).toHaveTextContent(
      /Esperando a confirmação do pagamento/,
    )
    expect(screen.queryByRole('button', { name: 'Ser Família Fundadora' })).not.toBeInTheDocument()
    const before = planCalls
    for (let i = 0; i < 30; i += 1) await act(() => vi.advanceTimersByTimeAsync(5000))

    expect(refreshCalls).toBe(24)
    // Giving up reads the plan once more and says so.
    expect(await screen.findByText(/O pagamento ainda não foi confirmado/)).toBeInTheDocument()
    expect(screen.queryByText(/Esperando a confirmação/)).not.toBeInTheDocument()
    // The wait is over: the owner can start a new checkout.
    expect(screen.getByRole('button', { name: 'Ser Família Fundadora' })).toBeInTheDocument()
    await waitFor(() => expect(planCalls).toBeGreaterThan(before))
    await act(() => vi.advanceTimersByTimeAsync(30_000))
    expect(refreshCalls).toBe(24)
  })

  it('leavingTheReturnStopsPolling', async () => {
    const workspace = seedMockFamily()
    let refreshCalls = 0
    plan(workspace.id, { ...FREE, openOrder: { id: 'o1', status: 'CREATED', refundReason: null } })
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refresh`, () => {
        refreshCalls += 1
        return HttpResponse.json({ id: 'o1', status: 'CREATED', refundReason: null })
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { router } = renderPlan('/mais/plano?pedido=o1')
    await waitFor(() => expect(refreshCalls).toBe(1))

    await act(() => router.navigate('/mais/plano'))
    await act(() => vi.advanceTimersByTimeAsync(30_000))

    expect(refreshCalls).toBe(1)
    expect(screen.getByRole('button', { name: 'Ser Família Fundadora' })).toBeInTheDocument()
  })

  it('waitingOrderHidesTheBuyButton', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, {
      ...FREE,
      openOrder: { id: 'o1', status: 'CREATED', refundReason: null },
    })
    renderPlan('/mais/plano?pedido=o1')

    expect(await screen.findByRole('status')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ser Família Fundadora' })).not.toBeInTheDocument()
  })

  it('openOrderWithoutReturnStillShowsTheBuyButton', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, {
      ...FREE,
      openOrder: { id: 'o1', status: 'CREATED', refundReason: null },
    })
    renderPlan()

    expect(await screen.findByRole('button', { name: 'Ser Família Fundadora' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('returnWithoutAMatchingOrderDoesNotPoll', async () => {
    const workspace = seedMockFamily()
    let refreshCalls = 0
    plan(workspace.id, FREE)
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refresh`, () => {
        refreshCalls += 1
        return HttpResponse.json({ id: 'o1', status: 'CREATED', refundReason: null })
      }),
    )
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderPlan('/mais/plano?pedido=o1')

    expect(await screen.findByRole('button', { name: 'Ser Família Fundadora' })).toBeInTheDocument()
    // Longer than several polling intervals: a stray poll would have run by now.
    await act(() => vi.advanceTimersByTimeAsync(30_000))
    expect(refreshCalls).toBe(0)
  })

  it('expiredOrderIsExplained', async () => {
    const workspace = seedMockFamily()
    let expired = false
    plan(workspace.id, () =>
      expired ? FREE : { ...FREE, openOrder: { id: 'o1', status: 'CREATED', refundReason: null } },
    )
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refresh`, () => {
        expired = true
        return HttpResponse.json({ id: 'o1', status: 'EXPIRED', refundReason: null })
      }),
    )
    renderPlan('/mais/plano?pedido=o1')

    expect(await screen.findByText(/O pagamento não foi concluído/)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Ser Família Fundadora' })).toBeInTheDocument()
  })

  it('ownerPicksATheme', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FOUNDER)
    let body: unknown
    let puts = 0
    server.use(
      http.put(
        `${API_URL}/v1/workspaces/${workspace.id}/billing/plan/theme`,
        async ({ request }) => {
          puts += 1
          body = await request.json()
          return new HttpResponse(null, { status: 204 })
        },
      ),
    )
    renderPlan()

    await screen.findByText('★ Família Fundadora')
    expect(screen.getByRole('radio', { name: /Verde-azulado/ })).toBeChecked()
    // The same theme again sends nothing.
    fireEvent.click(screen.getByRole('radio', { name: /Verde-azulado/ }))
    fireEvent.click(screen.getByRole('radio', { name: /Oceano/ }))

    await waitFor(() => expect(body).toEqual({ theme: 'OCEAN' }))
    expect(puts).toBe(1)
  })

  it('pickedThemeStaysCheckedWhileSaving', async () => {
    const workspace = seedMockFamily()
    let theme: PlanDetails['theme'] = 'TEAL'
    plan(workspace.id, () => ({ ...FOUNDER, theme }))
    let answer: (() => void) | undefined
    server.use(
      http.put(`${API_URL}/v1/workspaces/${workspace.id}/billing/plan/theme`, async () => {
        await new Promise<void>((resolve) => {
          answer = resolve
        })
        theme = 'OCEAN'
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderPlan()

    await screen.findByText('★ Família Fundadora')
    fireEvent.click(screen.getByRole('radio', { name: /Oceano/ }))

    expect(screen.getByRole('radio', { name: /Oceano/ })).toBeChecked()
    await waitFor(() => expect(answer).toBeDefined())
    expect(screen.getByRole('radio', { name: /Oceano/ })).toBeChecked()
    act(() => answer?.())
    await waitFor(() => expect(theme).toBe('OCEAN'))
    expect(screen.getByRole('radio', { name: /Oceano/ })).toBeChecked()
  })

  it('themeErrorShowsDetail', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FOUNDER)
    server.use(
      http.put(`${API_URL}/v1/workspaces/${workspace.id}/billing/plan/theme`, () =>
        problem(403, 'plan.required', 'Este recurso é do plano Família.'),
      ),
    )
    renderPlan()

    await screen.findByText('★ Família Fundadora')
    fireEvent.click(screen.getByRole('radio', { name: /Floresta/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Este recurso é do plano Família.')
  })

  it('refundErrorShowsDetail', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FOUNDER)
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/orders/o1/refund`, () =>
        problem(409, 'billing.refund-window-closed', 'O prazo de 7 dias já passou.'),
      ),
    )
    renderPlan()

    fireEvent.click(await screen.findByRole('button', { name: 'Pedir o dinheiro de volta' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Pedir os R$ 89,00 de volta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('O prazo de 7 dias já passou.')
  })

  it('nonOwnerFounderHasNoControls', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'ADULT'
    plan(workspace.id, { ...FOUNDER, canRefund: false })
    renderPlan()

    expect(await screen.findByText('★ Família Fundadora')).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText('Quem cuida do plano é o Dono da família.')).toBeInTheDocument()
  })

  it('offlineDisablesRefundAndThemes', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FOUNDER)
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderPlan()

    expect(await screen.findByRole('button', { name: 'Pedir o dinheiro de volta' })).toBeDisabled()
    for (const radio of screen.getAllByRole('radio')) expect(radio).toBeDisabled()
  })

  it('founderWithinSevenDaysCanRefund', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FOUNDER)
    renderPlan()

    expect(await screen.findByText('★ Família Fundadora')).toBeInTheDocument()
    expect(screen.getByText('Vale até 17/11/2027')).toBeInTheDocument()
    expect(screen.getByText('Não existe cobrança automática.')).toBeInTheDocument()
    expect(
      screen.getByText('Desistiu? Até 24/11 você recebe os R$ 89,00 de volta.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('radiogroup')).toBeInTheDocument()
    expect(screen.getAllByRole('radio')).toHaveLength(4)
    fireEvent.click(screen.getByRole('button', { name: 'Pedir o dinheiro de volta' }))
    expect(
      await screen.findByRole('button', { name: 'Pedir os R$ 89,00 de volta' }),
    ).toBeInTheDocument()
  })

  it('founderAfterSevenDaysShowsNoAutoCharge', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, { ...FOUNDER, canRefund: false, refundDeadline: null })
    renderPlan()

    expect(await screen.findByText('Não existe cobrança automática.')).toBeInTheDocument()
    expect(screen.getByText('Seu plano vale até 17/11/2027.')).toBeInTheDocument()
    expect(screen.getByText(new RegExp(legalConfig.contactEmail))).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Pedir o dinheiro de volta' }),
    ).not.toBeInTheDocument()
  })

  it('duplicateShowsTwoPayments', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, {
      ...FOUNDER,
      canRefund: false,
      openOrder: { id: 'o2', status: 'REFUND_PENDING', refundReason: 'DUPLICATE' },
    })
    renderPlan()

    expect(
      await screen.findByText('Recebemos dois pagamentos. Devolvemos um deles pelo Asaas.'),
    ).toBeInTheDocument()
  })

  it('customerRefundPendingMentionsCardDelay', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, {
      ...FREE,
      canPurchase: false,
      openOrder: { id: 'o1', status: 'REFUND_PENDING', refundReason: 'CUSTOMER' },
    })
    renderPlan()

    expect(await screen.findByText('Reembolso em processamento')).toBeInTheDocument()
    expect(screen.getByText(/até 10 dias úteis/)).toBeInTheDocument()
  })

  it('checkoutErrorShowsDetail', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FREE)
    server.use(
      http.post(`${API_URL}/v1/workspaces/${workspace.id}/billing/checkout`, () =>
        problem(502, 'billing.checkout-failed', 'O Asaas não respondeu. Tente de novo.'),
      ),
    )
    renderPlan()

    fireEvent.click(await screen.findByRole('button', { name: 'Ser Família Fundadora' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O Asaas não respondeu. Tente de novo.',
    )
  })

  it('offlineDisablesButtons', async () => {
    const workspace = seedMockFamily()
    plan(workspace.id, FREE)
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderPlan()

    expect(await screen.findByRole('button', { name: 'Ser Família Fundadora' })).toBeDisabled()
    expect(screen.getByText('Você está sem internet.')).toBeInTheDocument()
  })
})
