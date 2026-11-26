import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { ReceiptItemsStep } from './ReceiptItemsStep'

afterEach(cleanup)

function render(defaultDate: string | undefined, onSubmit = vi.fn()) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  renderWithProviders(
    <ReceiptItemsStep
      ws={workspace.id}
      uf="RS"
      defaultDate={defaultDate}
      pending={false}
      error={null}
      onSubmit={onSubmit}
    />,
  )
  return onSubmit
}

async function fillItem() {
  fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'arroz' } })
  fireEvent.click(await screen.findByRole('button', { name: 'Arroz tipo 1' }))
  fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '4,99' } })
}

describe('ReceiptItemsStep', () => {
  it('priceAsDecimalText', async () => {
    const onSubmit = render('2026-11-10')

    await fillItem()
    fireEvent.change(screen.getByLabelText('Quantidade (opcional)'), { target: { value: '1,5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))

    expect(onSubmit).toHaveBeenCalledWith({
      issuedOn: '2026-11-10',
      items: [
        {
          productId: '00000000-0000-4000-8000-00000000a001',
          newProductName: null,
          unit: 'KG',
          quantity: '1.5',
          unitPrice: '4.99',
        },
      ],
    })
  })

  it('newProductGoesByName', async () => {
    const onSubmit = render('2026-11-10')

    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'pão' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Novo: pão' }))
    fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '1,50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))

    expect(onSubmit.mock.calls[0]?.[0].items).toEqual([
      { productId: null, newProductName: 'pão', unit: 'UN', quantity: null, unitPrice: '1.50' },
    ])
  })

  it('dateRequiredWhenUnknown', async () => {
    render(undefined)
    await fillItem()

    expect(screen.getByRole('button', { name: 'Gravar' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Data da compra'), { target: { value: '2026-11-10' } })
    expect(screen.getByRole('button', { name: 'Gravar' })).toBeEnabled()
  })

  it('anInvalidPriceKeepsSaveOff', async () => {
    render('2026-11-10')
    await fillItem()

    fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '4,999' } })

    expect(screen.getByRole('button', { name: 'Gravar' })).toBeDisabled()
  })

  it('addsAndRemovesItems', () => {
    render('2026-11-10')

    fireEvent.click(screen.getByRole('button', { name: 'Adicionar item' }))
    expect(screen.getAllByLabelText('Produto')).toHaveLength(2)
    fireEvent.click(screen.getByRole('button', { name: 'Remover item 2' }))
    expect(screen.getAllByLabelText('Produto')).toHaveLength(1)
  })

  it('stopsAtTwoHundredItems', { timeout: 30000 }, () => {
    render('2026-11-10')

    for (let count = 1; count < 200; count += 1) {
      fireEvent.click(screen.getByRole('button', { name: 'Adicionar item' }))
    }

    expect(screen.getAllByLabelText('Produto')).toHaveLength(200)
    expect(screen.getByRole('button', { name: 'Adicionar item' })).toBeDisabled()
  })
})
