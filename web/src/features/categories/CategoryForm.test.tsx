import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import type { CategoryView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { CategoryForm } from './CategoryForm'

afterEach(cleanup)

const basket: CategoryView = {
  id: crypto.randomUUID(),
  name: 'Cesta',
  kind: 'EXPENSE',
  icon: '🧺',
  color: null,
  parentId: null,
  archivedAt: null,
}

function renderForm(category?: CategoryView) {
  const workspace = seedMockFamily()
  return renderWithProviders(
    <CategoryForm
      workspace={workspace}
      kind="EXPENSE"
      categories={category ? [category] : []}
      category={category}
      onDone={() => {}}
    />,
  )
}

describe('CategoryForm', () => {
  it('iconGridIsRadioGroup', () => {
    renderForm()

    const group = screen.getByRole('radiogroup', { name: 'Ícone' })
    const radios = within(group).getAllByRole('radio')
    expect(radios).toHaveLength(24)
    expect(radios[0]).toHaveAccessibleName('Mercado')
    expect(radios[1]).toHaveAccessibleName('Restaurante')
    expect(within(group).getByRole('radio', { name: 'Investimentos' })).toBeInTheDocument()
  })

  it('unknownIconComesFirst', () => {
    renderForm(basket)

    const radios = within(screen.getByRole('radiogroup', { name: 'Ícone' })).getAllByRole('radio')
    expect(radios).toHaveLength(25)
    expect(radios[0]).toHaveAccessibleName('🧺')
    expect(radios[0]).toBeChecked()
  })

  it('offers Sem cor first and names each color', () => {
    renderForm()

    const colors = within(screen.getByRole('radiogroup', { name: 'Cor' })).getAllByRole('radio')
    expect(colors.map((radio) => radio.getAttribute('aria-label'))).toEqual([
      'Sem cor',
      'Verde-azulado',
      'Azul',
      'Índigo',
      'Roxo',
      'Rosa',
      'Vermelho',
      'Laranja',
      'Verde',
    ])
    expect(colors[0]).toBeChecked()
  })
})
