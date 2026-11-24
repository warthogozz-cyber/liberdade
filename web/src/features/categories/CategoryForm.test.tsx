import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type CategoryView } from '@/shared/api'
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
  isFixed: false,
}

function renderForm(
  category?: CategoryView,
  kind: 'EXPENSE' | 'INCOME' = 'EXPENSE',
  moduleOn = false,
) {
  const workspace = seedMockFamily()
  if (moduleOn) mockState.modules[workspace.id] = { planning: true }
  return renderWithProviders(
    <CategoryForm
      workspace={workspace}
      kind={kind}
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

describe('CategoryForm fixed toggle', () => {
  const settle = () => new Promise((resolve) => setTimeout(resolve, 50))

  it('showsGastoFixoOnlyForExpenseCategories', async () => {
    renderForm(basket, 'EXPENSE', true)
    expect(await screen.findByRole('checkbox', { name: 'Gasto fixo' })).not.toBeChecked()
    cleanup()

    renderForm({ ...basket, kind: 'INCOME' }, 'INCOME', true)
    await settle()
    expect(screen.queryByRole('checkbox', { name: 'Gasto fixo' })).not.toBeInTheDocument()
  })

  it('hidesTheToggleWithTheModuleOff', async () => {
    renderForm(basket)
    await settle()
    expect(screen.queryByRole('checkbox', { name: 'Gasto fixo' })).not.toBeInTheDocument()
  })

  it('alwaysSendsTheFlagWhenShownAndNothingWhenHidden', async () => {
    const bodies: Record<string, unknown>[] = []
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/categories/:id`, async ({ request }) => {
        bodies.push((await request.clone().json()) as Record<string, unknown>)
        return undefined
      }),
    )
    renderForm(basket, 'EXPENSE', true)
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Gasto fixo' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Cesta 2' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({ name: 'Cesta 2', isFixed: true })
    cleanup()

    renderForm(basket)
    fireEvent.change(screen.getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Cesta 3' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[1]).toEqual({ name: 'Cesta 3' })
  })
})
