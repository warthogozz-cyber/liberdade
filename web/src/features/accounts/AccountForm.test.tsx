import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { AccountForm } from './AccountForm'

afterEach(cleanup)

function renderForm() {
  const workspace = seedMockFamily()
  return renderWithProviders(<AccountForm workspace={workspace} onDone={() => {}} />)
}

describe('AccountForm', () => {
  it('showsTypeHint', () => {
    renderForm()
    expect(screen.getByText('Para o dia a dia: salário, contas, Pix.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('radio', { name: /Cartão de crédito/ }))

    expect(
      screen.getByText(
        'Os gastos deixam o saldo negativo. Pagar a fatura é uma transferência de outra conta para o cartão.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByText('Para o dia a dia: salário, contas, Pix.')).not.toBeInTheDocument()
  })

  it('names the type group and describes it with the hint', () => {
    renderForm()

    fireEvent.click(screen.getByRole('radio', { name: /Cartão de crédito/ }))

    expect(screen.getByRole('radiogroup', { name: 'Tipo' })).toHaveAccessibleDescription(
      'Os gastos deixam o saldo negativo. Pagar a fatura é uma transferência de outra conta para o cartão.',
    )
  })

  it('does not save without a name', () => {
    renderForm()

    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled()
  })
})
