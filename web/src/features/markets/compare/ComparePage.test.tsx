import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, type MockPrice, type MockProduct } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { ComparePage } from './ComparePage'

beforeEach(() => {
  // The API calls a price old after 60 days; the mock reads the clock, so it is fixed here.
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-27T12:00:00Z'))
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

const M1 = 'osm-n-9001'
const M2 = 'osm-n-9002'
const M3 = 'osm-n-9003'
const M5 = 'osm-n-9005'

const id = (n: number) => `00000000-0000-4000-8000-0000000d${String(n).padStart(4, '0')}`
const products: MockProduct[] = Array.from({ length: 9 }, (_, index) => ({
  id: id(index + 1),
  name: `Produto Exemplo ${index + 1}`,
  unit: 'KG',
  gtin: null,
}))

const price = (
  marketRef: string,
  n: number,
  unitPrice: string,
  observedOn = '2026-11-10',
  unit?: MockPrice['unit'],
): MockPrice => ({ marketRef, productId: id(n), unitPrice, observedOn, ...(unit ? { unit } : {}) })

/**
 * A list of nine items. Mercado Exemplo 1 has a price for all of them; Mercado Exemplo 2 for all but
 * the 8th, and the 9th is priced per unit there (so 7 items are common, 1 is missing, 1 differs).
 * Mercados Exemplo 3 and 5 price the first item only.
 */
const prices: MockPrice[] = [
  price(M1, 1, '4.9900', '2026-11-12'),
  price(M1, 2, '7.4900', '2026-01-05'),
  ...[3, 4, 5, 6, 7, 8, 9].map((n) => price(M1, n, '3.0000')),
  price(M2, 1, '5.4900'),
  ...[2, 3, 4, 5, 6, 7].map((n) => price(M2, n, '3.5000')),
  price(M2, 9, '3.0000', '2026-11-10', 'UN'),
  price(M3, 1, '6.0000'),
  price(M5, 1, '6.5000'),
]

function render() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  mockState.markets[workspace.id] = {
    family: [],
    home: null,
    osmLinks: {},
    archivedOsm: [],
    products,
    prices,
    list: products.map((product, index) => ({
      id: `00000000-0000-4000-8000-0000000e${String(index + 1).padStart(4, '0')}`,
      productId: product.id,
      quantity: index === 0 ? '2' : '1',
      checked: false,
    })),
  }
  return renderWithProviders(<ComparePage />, {
    path: '/mais/feira/comparar',
    route: '/mais/feira/comparar',
  })
}

const box = (name: string) => screen.findByRole('checkbox', { name })

describe('ComparePage', () => {
  it('helpText', async () => {
    localStorage.setItem('liberdade.tips', 'on')
    render()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'O total conta só os itens com preço em todos os mercados escolhidos.',
    )
  })

  it('suggestedPreselected', async () => {
    render()

    expect(await box('Mercado Exemplo 1')).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Mercado Exemplo 2' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Mercado Exemplo 3' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Mercado Exemplo 5' })).not.toBeChecked()
  })

  it('oneToThreeMarkets', async () => {
    render()
    expect(await box('Mercado Exemplo 1')).toBeChecked()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Mercado Exemplo 3' }))
    expect(screen.getByRole('checkbox', { name: 'Mercado Exemplo 5' })).toBeDisabled()
    // A chosen one is never disabled, so it can be taken off again.
    expect(screen.getByRole('checkbox', { name: 'Mercado Exemplo 3' })).toBeEnabled()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Mercado Exemplo 3' }))
    expect(screen.getByRole('checkbox', { name: 'Mercado Exemplo 5' })).toBeEnabled()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Mercado Exemplo 2' }))
    expect(screen.getByText('Escolha pelo menos dois mercados.')).toBeInTheDocument()
    expect(screen.queryByText(/Nos \d+ itens/)).not.toBeInTheDocument()
  })

  it('rowsWithDateAndOld', async () => {
    render()

    const first = (await screen.findByText('Produto Exemplo 1')).closest('li') as HTMLElement
    expect(within(first).getByText('R$ 4,99 (12/11)')).toBeInTheDocument()
    expect(within(first).getByText('R$ 5,49 (10/11)')).toBeInTheDocument()
    expect(within(first).queryByText('antigo')).not.toBeInTheDocument()

    const second = screen.getByText('Produto Exemplo 2').closest('li') as HTMLElement
    expect(within(second).getByText('antigo')).toBeInTheDocument()

    const eighth = screen.getByText('Produto Exemplo 8').closest('li') as HTMLElement
    expect(within(eighth).getByText('—')).toBeInTheDocument()
  })

  it('footerCounts', async () => {
    render()

    expect(await screen.findByText('Nos 7 itens que os mercados têm:')).toBeInTheDocument()
    expect(screen.getByText('1 item sem preço em Mercado Exemplo 2')).toBeInTheDocument()
    expect(screen.queryByText(/sem preço em Mercado Exemplo 1/)).not.toBeInTheDocument()
    expect(
      screen.getByText('1 item com unidade diferente ficou fora do total.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Preços de notas registradas pela família. Datas ao lado de cada preço.'),
    ).toBeInTheDocument()
  })

  it('totalsInChosenOrder', async () => {
    render()
    await screen.findByText('Nos 7 itens que os mercados têm:')

    // Mercado Exemplo 1 comes off and goes back on last: the order of the choosing, not of the totals.
    fireEvent.click(screen.getByRole('checkbox', { name: 'Mercado Exemplo 1' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Mercado Exemplo 3' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Mercado Exemplo 1' }))
    await screen.findByText('No 1 item que os mercados têm:')

    const totals = within(screen.getByRole('list', { name: 'Totais' })).getAllByRole('listitem')
    expect(totals.map((each) => each.textContent)).toEqual([
      expect.stringMatching(/^Mercado Exemplo 2.*R\$\s10,98$/),
      expect.stringMatching(/^Mercado Exemplo 3.*R\$\s12,00$/),
      expect.stringMatching(/^Mercado Exemplo 1.*R\$\s9,98$/),
    ])
    // No total stands out: they all share one look.
    expect(new Set(totals.map((each) => each.className)).size).toBe(1)
  })

  it('neutralCopy', async () => {
    const { container } = render()
    await waitFor(() => expect(screen.getByText('Nos 7 itens que os mercados têm:')).toBeVisible())

    expect(container.textContent ?? '').not.toMatch(/barato|economi|melhor|vencedor/i)
  })
})
