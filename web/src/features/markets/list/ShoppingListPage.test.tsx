import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { FAKE_PRODUCTS, mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { marketsKeys } from '@/shared/api/markets'
import { renderWithProviders } from '@/shared/testing'
import { ShoppingListPage } from './ShoppingListPage'

afterEach(cleanup)

type Role = 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER'

const ROOT = `${API_URL}/v1/workspaces/:ws/markets/list`
const RICE = '00000000-0000-4000-8000-00000000c001'
const BEANS = '00000000-0000-4000-8000-00000000c002'
const MILK = '00000000-0000-4000-8000-00000000c003'

/** A family with the Feira on and a list of three items (rice, beans, milk), in that order. */
function render(role: Role = 'OWNER', items: 'three' | 'none' = 'three') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { markets: true }
  mockState.markets[workspace.id] = {
    family: [],
    home: null,
    osmLinks: {},
    archivedOsm: [],
    list:
      items === 'none'
        ? []
        : [
            { id: RICE, productId: FAKE_PRODUCTS[0]!.id, quantity: '2', checked: false },
            { id: BEANS, productId: FAKE_PRODUCTS[1]!.id, quantity: '1', checked: false },
            { id: MILK, productId: FAKE_PRODUCTS[2]!.id, quantity: '1', checked: false },
          ],
  }
  const rendered = renderWithProviders(<ShoppingListPage />, {
    path: '/mais/feira/lista',
    route: '/mais/feira/lista',
  })
  return { workspace, ...rendered }
}

/** The list row of an item, by its name. */
const rowOf = async (name: string) => (await screen.findByText(name)).closest('li') as HTMLElement

describe('ShoppingListPage', () => {
  it('helpText', async () => {
    localStorage.setItem('liberdade.tips', 'on')
    render()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'A lista é da família toda e muda ao vivo em todos os aparelhos.',
    )
  })

  it('addByPickerOrNewName', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(ROOT, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({}, { status: 201 })
      }),
    )
    render()
    await screen.findByText('Arroz tipo 1')

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Leite integral' }))
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ productId: FAKE_PRODUCTS[2]!.id })

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'Café Exemplo' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Novo: Café Exemplo' }))
    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[1]).toMatchObject({ newProductName: 'Café Exemplo' })
  })

  it.each([
    ['list.quantity_too_large', 'A quantidade ficaria grande demais. Use um número menor.'],
    ['list.full', 'A lista já tem o máximo de 300 itens.'],
  ])('addRefusalSaysWhy %s', async (code, text) => {
    server.use(http.post(ROOT, () => problem(422, code, 'detalhe do servidor')))
    render()
    await screen.findByText('Arroz tipo 1')

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Leite integral' }))

    expect(await screen.findByText(text)).toBeVisible()
    expect(screen.queryByText('Não deu para adicionar à lista. Tente de novo.')).toBeNull()
  })

  it('checkIsOptimistic', async () => {
    server.use(
      http.patch(`${ROOT}/:id`, async () => {
        await delay(150)
        return HttpResponse.json({}, { status: 500 })
      }),
    )
    render()
    const row = await rowOf('Arroz tipo 1')

    fireEvent.click(within(row).getByRole('checkbox', { name: 'Marcar Arroz tipo 1' }))

    // At once, before the server answers: struck through and under "Pegos".
    const picked = await screen.findByRole('region', { name: 'Pegos' })
    expect(within(picked).getByText('Arroz tipo 1')).toHaveClass('line-through')

    // The 500 puts it back and warns.
    expect(await screen.findByRole('alert')).toHaveTextContent('Não deu para salvar a mudança')
    expect(screen.queryByRole('region', { name: 'Pegos' })).not.toBeInTheDocument()
    expect(screen.getByText('Arroz tipo 1')).not.toHaveClass('line-through')
  })

  it('quantityAsDecimalText', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch(`${ROOT}/:id`, async ({ request, params }) => {
        bodies.push({ id: params.id, ...((await request.json()) as object) })
        return HttpResponse.json({})
      }),
    )
    render()
    const row = await rowOf('Arroz tipo 1')

    const field = within(row).getByLabelText('Quantidade de Arroz tipo 1')
    fireEvent.change(field, { target: { value: '1,5' } })
    fireEvent.blur(field)

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ id: RICE, quantity: '1.5' })
  })

  it('moveUpAndDownByKeyboard', async () => {
    const orders: unknown[] = []
    server.use(
      http.put(`${ROOT}/order`, async ({ request }) => {
        orders.push(await request.json())
        return HttpResponse.json({ items: [] })
      }),
    )
    render()
    const row = await rowOf('Feijão preto')

    fireEvent.click(within(row).getByRole('button', { name: 'Mais opções de Feijão preto' }))
    fireEvent.click(within(row).getByRole('button', { name: 'Subir' }))

    await waitFor(() => expect(orders).toHaveLength(1))
    expect(orders[0]).toEqual({ ids: [BEANS, RICE, MILK] })

    const last = await rowOf('Leite integral')
    fireEvent.click(within(last).getByRole('button', { name: 'Mais opções de Leite integral' }))
    expect(within(last).getByRole('button', { name: 'Descer' })).toBeDisabled()
  })

  it('staleOrderReloads', async () => {
    server.use(
      http.put(`${ROOT}/order`, () =>
        problem(409, 'list.order_stale', 'A lista mudou. Recarregue e tente de novo.'),
      ),
    )
    const { queryClient, workspace } = render()
    const row = await rowOf('Feijão preto')
    const key = marketsKeys.list(workspace.id)
    const before = queryClient.getQueryState(key)!.dataUpdatedAt

    fireEvent.click(within(row).getByRole('button', { name: 'Mais opções de Feijão preto' }))
    fireEvent.click(within(row).getByRole('button', { name: 'Subir' }))

    expect(await screen.findByText('A lista mudou em outro aparelho.')).toBeVisible()
    await waitFor(() =>
      expect(queryClient.getQueryState(key)!.dataUpdatedAt).toBeGreaterThan(before),
    )
  })

  it('youthCannotRemove', async () => {
    render('YOUTH')
    const row = await rowOf('Arroz tipo 1')
    // Mark one, so that "Limpar os pegos" would show for someone allowed.
    fireEvent.click(within(row).getByRole('checkbox', { name: 'Marcar Arroz tipo 1' }))
    await screen.findByRole('region', { name: 'Pegos' })

    expect(screen.getByLabelText('Produto')).toBeVisible()
    expect(screen.getAllByRole('checkbox').length).toBeGreaterThan(0)
    expect(screen.getAllByLabelText(/^Quantidade de /).length).toBeGreaterThan(0)
    const open = await rowOf('Feijão preto')
    fireEvent.click(within(open).getByRole('button', { name: 'Mais opções de Feijão preto' }))
    expect(within(open).getByRole('button', { name: 'Subir' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remover' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Limpar os pegos' })).not.toBeInTheDocument()
  })

  it('ownerCanRemoveAndClearChecked', async () => {
    render('OWNER')
    const row = await rowOf('Arroz tipo 1')
    fireEvent.click(within(row).getByRole('checkbox', { name: 'Marcar Arroz tipo 1' }))
    await screen.findByRole('region', { name: 'Pegos' })

    fireEvent.click(screen.getByRole('button', { name: 'Limpar os pegos' }))
    fireEvent.click(screen.getByRole('button', { name: 'Limpar' }))
    await waitFor(() => expect(screen.queryByText('Arroz tipo 1')).not.toBeInTheDocument())

    const beans = await rowOf('Feijão preto')
    fireEvent.click(within(beans).getByRole('button', { name: 'Mais opções de Feijão preto' }))
    fireEvent.click(within(beans).getByRole('button', { name: 'Remover' }))
    await waitFor(() => expect(screen.queryByText('Feijão preto')).not.toBeInTheDocument())
  })

  it('viewerReadOnly', async () => {
    render('VIEWER')
    await screen.findByText('Arroz tipo 1')

    expect(screen.queryByLabelText('Produto')).not.toBeInTheDocument()
    for (const box of screen.getAllByRole('checkbox')) expect(box).toBeDisabled()
    expect(screen.queryByLabelText(/^Quantidade de /)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Mais opções/ })).not.toBeInTheDocument()
  })

  it('liveEventRefetches', async () => {
    let reads = 0
    server.use(
      http.get(ROOT, () => {
        reads += 1
        return HttpResponse.json({ items: [] })
      }),
    )
    const { queryClient, workspace } = render()
    await screen.findByText('A lista está vazia. Adicione o que falta em casa.')
    expect(reads).toBe(1)

    await queryClient.invalidateQueries({ queryKey: ['ws', workspace.id, 'markets', 'list'] })

    await waitFor(() => expect(reads).toBe(2))
  })

  it('emptyState', async () => {
    render('OWNER', 'none')

    expect(
      await screen.findByText('A lista está vazia. Adicione o que falta em casa.'),
    ).toBeVisible()
  })
})
