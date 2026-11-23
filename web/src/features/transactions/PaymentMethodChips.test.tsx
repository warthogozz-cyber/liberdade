import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PaymentMethod } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { PaymentMethodChips } from './PaymentMethodChips'

afterEach(cleanup)

const METHODS: PaymentMethod[] = [
  'PIX',
  'CREDIT_CARD',
  'DEBIT_CARD',
  'BOLETO',
  'BANK_TRANSFER',
  'CASH',
]

function renderChips(value: PaymentMethod | null) {
  const onChange = vi.fn()
  renderWithProviders(<PaymentMethodChips value={value} onChange={onChange} methods={METHODS} />)
  return onChange
}

describe('PaymentMethodChips', () => {
  it('shows the six methods in order, none pressed', () => {
    renderChips(null)

    const buttons = screen.getAllByRole('button')
    expect(buttons.map((button) => button.textContent)).toEqual([
      'Pix',
      'Crédito',
      'Débito',
      'Boleto',
      'TED',
      'Dinheiro',
    ])
    for (const button of buttons) expect(button).toHaveAttribute('aria-pressed', 'false')
  })

  it('picks a method', () => {
    const onChange = renderChips(null)
    fireEvent.click(screen.getByRole('button', { name: 'Pix' }))
    expect(onChange).toHaveBeenLastCalledWith('PIX')
  })

  it('clears the choice when the marked one is tapped again', () => {
    const onChange = renderChips('PIX')
    expect(screen.getByRole('button', { name: 'Pix' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Pix' }))
    expect(onChange).toHaveBeenLastCalledWith(null)
  })
})
