import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { ProductsPage } from './ProductsPage'

afterEach(cleanup)

const A_ID = '00000000-0000-4000-8000-00000000b001'
const B_ID = '00000000-0000-4000-8000-00000000b002'

type Role = 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER'

/** A family with the Feira on and two products whose names are alike (one suggested pair). */
function render(role: Role = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { markets: true }
  mockState.markets[workspace.id] = {
    family: [],
    home: null,
    osmLinks: {},
    archivedOsm: [],
    products: [
      { id: A_ID, name: 'Café Exemplo 500 g', unit: 'UN', gtin: null },
      { id: B_ID, name: 'Café Exemplo Torrado 500 g', unit: 'UN', gtin: null },
    ],
  }
  renderWithProviders(<ProductsPage />, {
    path: '/mais/feira/produtos',
    route: '/mais/feira/produtos',
  })
  return workspace
}

describe('ProductsPage', () => {
  it('listsPricesPerMarket', async () => {
    render()

    const row = (await screen.findByText('Arroz tipo 1')).closest('li') as HTMLElement
    expect(within(row).getByText('Mercado Exemplo 1')).toBeVisible()
    expect(within(row).getByText('R$ 4,99 · 12/11/2026')).toBeVisible()
    expect(within(row).getByText('Mercado Exemplo 2')).toBeVisible()
    expect(within(row).getByText('R$ 5,49 · 10/11/2026')).toBeVisible()
  })

  it('suggestionCard', async () => {
    render()

    expect(await screen.findByText('São o mesmo produto?')).toBeVisible()
    const card = screen.getByText('São o mesmo produto?').closest('li') as HTMLElement
    expect(within(card).getByText('Café Exemplo 500 g')).toBeVisible()
    expect(within(card).getByText('Café Exemplo Torrado 500 g')).toBeVisible()
    expect(within(card).getByRole('button', { name: 'Juntar' })).toBeVisible()
    expect(within(card).getByRole('button', { name: 'Não são' })).toBeVisible()
  })

  it('notTheSameHidesPair', async () => {
    const workspace = render()

    fireEvent.click(await screen.findByRole('button', { name: 'Não são' }))

    await waitFor(() => expect(screen.queryByText('São o mesmo produto?')).not.toBeInTheDocument())
    expect(mockState.markets[workspace.id]?.dismissed).toEqual([[A_ID, B_ID]])
  })

  it('mergesFromTheSuggestion', async () => {
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Juntar' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Juntar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(screen.queryByText('São o mesmo produto?')).not.toBeInTheDocument())
    expect(screen.queryByText('Café Exemplo Torrado 500 g')).not.toBeInTheDocument()
  })

  it('editsNameAndUnit', async () => {
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Editar Café Exemplo 500 g' }))
    fireEvent.change(screen.getByLabelText('Nome'), {
      target: { value: 'Café Exemplo Forte 500 g' },
    })
    fireEvent.change(screen.getByLabelText('Unidade'), { target: { value: 'KG' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))

    const edited = await screen.findByRole('button', { name: 'Editar Café Exemplo Forte 500 g' })
    expect(
      screen.queryByRole('button', { name: 'Editar Café Exemplo 500 g' }),
    ).not.toBeInTheDocument()
    expect(within(edited.closest('li') as HTMLElement).getByText('quilo')).toBeVisible()
  })

  it.each(['YOUTH', 'VIEWER'] as const)('%s sees no actions', async (role) => {
    render(role)

    expect(await screen.findByText('Arroz tipo 1')).toBeVisible()
    expect(screen.getAllByText('Café Exemplo 500 g')).toHaveLength(2)
    expect(await screen.findByText('São o mesmo produto?')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Juntar|Não são|Editar/ })).not.toBeInTheDocument()
  })
})
