import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { ProductPicker } from './ProductPicker'

afterEach(cleanup)

function render(onChange = vi.fn()) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  renderWithProviders(<ProductPicker ws={workspace.id} value={null} onChange={onChange} />)
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
})
