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
    const onSubmit = render(undefined)
    await fillItem()

    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))
    expect(onSubmit).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Data da compra'), { target: { value: '2026-11-10' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))
    expect(onSubmit).toHaveBeenCalled()
  })

  it('submitWithInvalidRowMarksItAndFocusesFirst', async () => {
    const onSubmit = render('2026-11-10')

    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))

    expect(onSubmit).not.toHaveBeenCalled()
    const product = screen.getByLabelText('Produto')
    expect(product).toHaveAttribute('aria-invalid', 'true')
    expect(product).toHaveAccessibleDescription('Escolha o produto.')
    expect(product).toHaveFocus()
    expect(screen.getByLabelText('Preço da unidade')).toHaveAttribute('aria-invalid', 'true')

    await fillItem()
    fireEvent.change(screen.getByLabelText('Quantidade (opcional)'), { target: { value: 'x' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))
    const quantity = screen.getByLabelText('Quantidade (opcional)')
    expect(quantity).toHaveAccessibleDescription('Confira a quantidade.')
    expect(quantity).toHaveFocus()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('unitPriceAcceptsFourDecimals', async () => {
    const onSubmit = render('2026-11-10')
    await fillItem()

    fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '4,999' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))

    expect(onSubmit.mock.calls[0]?.[0].items[0].unitPrice).toBe('4.999')
  })

  it('unitPriceRejectsFiveDecimalsAndZero', async () => {
    const onSubmit = render('2026-11-10')
    await fillItem()

    for (const bad of ['4,99999', '0', '0,0000']) {
      fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: bad } })
      fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))
      const price = screen.getByLabelText('Preço da unidade')
      expect(price).toHaveAttribute('aria-invalid', 'true')
      expect(price).toHaveAccessibleDescription('Confira o preço: maior que zero, até 4 casas.')
    }
    expect(onSubmit).not.toHaveBeenCalled()
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
