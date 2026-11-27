import { cleanup, fireEvent, screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { ProductPicker, type ListItem } from './ProductPicker'

afterEach(cleanup)

function render(onChange = vi.fn(), listItems?: ListItem[]) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  renderWithProviders(
    <ProductPicker ws={workspace.id} value={null} onChange={onChange} listItems={listItems} />,
  )
  return onChange
}

describe('ProductPicker', () => {
  it('searchOrNew', async () => {
    const onChange = render()

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite' } })

    expect(await screen.findByRole('button', { name: 'Leite integral' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Novo: leite' }))
    expect(onChange).toHaveBeenCalledWith({ kind: 'new', name: 'leite' })
  })

  it('choosingAnExistingProductHandsItsIdAndUnit', async () => {
    const onChange = render()

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Leite integral' }))

    expect(onChange).toHaveBeenCalledWith({
      kind: 'existing',
      id: '00000000-0000-4000-8000-00000000a003',
      name: 'Leite integral',
      unit: 'L',
    })
  })

  it('offersNoNewNameForAnExactMatch', async () => {
    render()

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'Leite integral' } })

    await screen.findByRole('button', { name: 'Leite integral' })
    expect(screen.queryByRole('button', { name: /^Novo:/ })).not.toBeInTheDocument()
  })

  it('newOptionHiddenWhilePending', async () => {
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/markets/products`, async () => {
        await delay(300)
        return HttpResponse.json({ products: [] })
      }),
    )
    render()

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite x' } })

    expect(screen.queryByRole('button', { name: /^Novo:/ })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Procurando…')
    expect(await screen.findByRole('button', { name: 'Novo: leite x' })).toBeVisible()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('newOptionShownAfterSearchSettlesWithoutMatch', async () => {
    render()

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'zzz sem par' } })

    expect(await screen.findByRole('button', { name: 'Novo: zzz sem par' })).toBeVisible()
  })

  it('newOptionHiddenWhenExactMatchExists', async () => {
    render()

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite integral' } })

    await screen.findByRole('button', { name: 'Leite integral' })
    expect(screen.queryByRole('button', { name: /^Novo:/ })).not.toBeInTheDocument()
  })

  it('listItemsFirst', async () => {
    const onChange = render(vi.fn(), [
      { id: '00000000-0000-4000-8000-00000000c0ff', name: 'Zeta leite em pó', unit: 'UN' },
    ])

    // Before anything is typed, the list's items are the candidates.
    fireEvent.click(screen.getByRole('button', { name: 'Zeta leite em pó' }))
    expect(onChange).toHaveBeenCalledWith({
      kind: 'existing',
      id: '00000000-0000-4000-8000-00000000c0ff',
      name: 'Zeta leite em pó',
      unit: 'UN',
    })

    // Once typed, they still come before the other products, though "Leite" sorts first.
    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite' } })
    await screen.findByRole('button', { name: 'Leite integral' })
    const names = screen.getAllByRole('button').map((each) => each.textContent)
    expect(names.indexOf('Zeta leite em pó')).toBeLessThan(names.indexOf('Leite integral'))
  })
})
