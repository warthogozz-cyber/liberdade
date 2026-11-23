import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, queryKeys, type DashboardView } from '@/shared/api'
import { db } from '@/shared/offline'
import {
  createTestQueryClient,
  outboxItem,
  outboxPayload,
  renderWithProviders,
} from '@/shared/testing'

const chart = vi.hoisted(() => ({
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
  on: vi.fn(),
}))

// jsdom draws no SVG: the chart library is replaced and the page's numbers, tables and links are
// what is tested.
vi.mock('echarts/core', () => ({ init: () => chart, use: vi.fn() }))
vi.mock('echarts/charts', () => ({ BarChart: {}, LineChart: {}, PieChart: {} }))
vi.mock('echarts/components', () => ({ DatasetComponent: {}, GridComponent: {} }))
vi.mock('echarts/renderers', () => ({ SVGRenderer: {} }))

import { DashboardPage } from './DashboardPage'

const DASHBOARD = `${API_URL}/v1/workspaces/:ws/dashboard`
const money = (amount: string) => ({ amount, currency: 'BRL' })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

const mercadoId = () => mockState.categories.find((category) => category.name === 'Mercado')!.id

/** PRD section 7.1, as the API answers it (DashboardIT.prdExampleThroughTheApi). */
function prdDashboard(month: string, byCategory = true): DashboardView {
  const [year, number] = month.split('-').map(Number)
  const days = new Date(Date.UTC(year, number, 0)).getUTCDate()
  return {
    month,
    income: money('5000.00'),
    expenses: money('1234.56'),
    result: money('3765.44'),
    balance: money('4765.44'),
    byCategory: byCategory
      ? [{ categoryId: mercadoId(), current: money('1234.56'), previous: money('0.00') }]
      : [],
    dailyBalance: Array.from({ length: days }, (_, index) => ({
      day: `${month}-${String(index + 1).padStart(2, '0')}`,
      balance: money(index < 3 ? '1000.00' : '4765.44'),
    })),
  }
}

function serve(byCategory = true) {
  const months: string[] = []
  server.use(
    http.get(DASHBOARD, ({ request }) => {
      const month = new URL(request.url).searchParams.get('month')!
      months.push(month)
      return HttpResponse.json(prdDashboard(month, byCategory))
    }),
  )
  return months
}

function renderPainel(route = '/painel') {
  return renderWithProviders(<DashboardPage />, { path: '/painel', route })
}

const card = async (name: string) =>
  (await screen.findByText(name, { selector: 'dt' })).parentElement!

describe('DashboardPage', () => {
  it('prdExample', async () => {
    seedMockFamily()
    serve()
    renderPainel()

    expect(await screen.findByRole('heading', { level: 1, name: 'Painel do mês' })).toBeVisible()
    expect(await within(await card('Saldo total')).findByText('R$ 4.765,44')).toBeInTheDocument()
    expect(within(await card('Receitas')).getByText('R$ 5.000,00')).toBeInTheDocument()
    expect(within(await card('Gastos')).getByText('R$ 1.234,56')).toBeInTheDocument()
    expect(within(await card('Resultado do mês')).getByText('R$ 3.765,44')).toBeInTheDocument()
    const [byCategory] = screen.getAllByRole('table', { hidden: true })
    const mercado = within(byCategory).getByRole('row', { name: /Mercado/, hidden: true })
    expect(within(mercado).getByText('R$ 1.234,56')).toBeInTheDocument()
    expect(within(mercado).getByText('100%')).toBeInTheDocument()
    expect(document.title).toBe('Painel do mês · Liberdade')
  })

  it('monthFromUrl', async () => {
    seedMockFamily()
    const months = serve()
    renderPainel('/painel?mes=2026-10')

    expect(await screen.findByText('Outubro de 2026')).toBeInTheDocument()
    await within(await card('Receitas')).findByText('R$ 5.000,00')
    expect(months).toEqual(['2026-10'])
  })

  it('writes the month to the URL', async () => {
    seedMockFamily()
    serve()
    const { router } = renderPainel()

    await screen.findByText('Novembro de 2026')
    fireEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))

    expect(await screen.findByText('Outubro de 2026')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?mes=2026-10')
  })

  it('categoryLinksToList', async () => {
    seedMockFamily()
    serve()
    renderPainel()

    const link = await screen.findByRole('link', { name: /Mercado/ })

    expect(link).toHaveAttribute('href', `/lancamentos?mes=2026-11&categoria=${mercadoId()}`)
  })

  it('linksToPortrait', async () => {
    seedMockFamily()
    serve()
    renderPainel('/painel?mes=2026-11')

    const link = await screen.findByRole('link', { name: 'Retrato do mês' })

    expect(link).toHaveAttribute('href', '/painel/retrato?mes=2026-11')
  })

  it('leaves Outras without a link', async () => {
    seedMockFamily()
    server.use(
      http.get(DASHBOARD, () =>
        HttpResponse.json({
          ...prdDashboard('2026-11'),
          byCategory: [
            { categoryId: mercadoId(), current: money('60.00'), previous: money('0.00') },
            { categoryId: null, current: money('40.00'), previous: money('0.00') },
          ],
        }),
      ),
    )
    renderPainel()

    await screen.findByRole('link', { name: /Mercado/ })
    const list = screen.getByRole('list', { name: 'Gastos por categoria' })
    const others = within(list).getByText('Outras').closest('li')!

    expect(within(others).queryByRole('link')).not.toBeInTheDocument()
    expect(within(others).getByText('40%')).toBeInTheDocument()
  })

  it('tablesForScreenReaders', async () => {
    seedMockFamily()
    serve()
    const { container } = renderPainel()

    await screen.findByRole('link', { name: /Mercado/ })
    const tables = screen.getAllByRole('table', { hidden: true })

    expect(tables).toHaveLength(3)
    expect(tables.map((table) => table.querySelector('caption')?.textContent)).toEqual([
      'Gastos por categoria',
      'vs. outubro',
      'Saldo no mês',
    ])
    expect(screen.getAllByText('Ver tabela')).toHaveLength(3)
    const charts = container.querySelectorAll('[aria-hidden="true"][data-chart]')
    expect(charts).toHaveLength(3)
  })

  it('queuedOnlyInCurrentMonth', async () => {
    const workspace = seedMockFamily()
    serve()
    await db.outbox.add(
      outboxItem('q1', {
        userId: mockState.user.id,
        workspaceId: workspace.id,
        payload: outboxPayload('q1', { amount: money('10.00'), occurredOn: '2026-11-10' }),
      }),
    )
    const current = renderPainel()

    expect(await within(await card('Gastos')).findByText('R$ 1.244,56')).toBeInTheDocument()
    expect(within(await card('Saldo total')).getByText('R$ 4.755,44')).toBeInTheDocument()
    expect(within(await card('Resultado do mês')).getByText('R$ 3.755,44')).toBeInTheDocument()
    expect(screen.getByText('Inclui lançamentos que ainda não foram enviados.')).toBeInTheDocument()
    current.unmount()

    renderPainel('/painel?mes=2026-10')

    expect(await within(await card('Gastos')).findByText('R$ 1.234,56')).toBeInTheDocument()
    expect(within(await card('Saldo total')).getByText('R$ 4.765,44')).toBeInTheDocument()
    expect(
      screen.queryByText('Inclui lançamentos que ainda não foram enviados.'),
    ).not.toBeInTheDocument()
  })

  it('noExpensesMessage', async () => {
    seedMockFamily()
    serve(false)
    renderPainel()

    expect(await screen.findByText('Nenhum gasto neste mês ainda.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Gastos por categoria' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Saldo no mês' })).toBeInTheDocument()
    expect(screen.getAllByRole('table', { hidden: true })).toHaveLength(1)
  })

  it('offlineShowsCache', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const workspace = seedMockFamily()
    server.use(http.get(DASHBOARD, () => HttpResponse.error()))
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(workspace.id, '2026-11'),
      prdDashboard('2026-11'),
      { updatedAt: Date.parse('2026-11-15T14:30:00Z') },
    )
    renderWithProviders(<DashboardPage />, { path: '/painel', queryClient })

    expect(await within(await card('Receitas')).findByText('R$ 5.000,00')).toBeInTheDocument()
    expect(screen.getByText('Mostrando dados de 15/11, 11:30.')).toBeInTheDocument()
  })

  it('says the numbers wait for the signal when offline without a cache', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    server.use(http.get(DASHBOARD, () => HttpResponse.error()))
    renderPainel()

    expect(
      await screen.findByText('Sem conexão. Os totais aparecem quando o sinal voltar.'),
    ).toBeInTheDocument()
  })
})
