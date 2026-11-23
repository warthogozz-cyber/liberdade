import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type CategoryView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { CategoriesPage } from './CategoriesPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function addCategory(overrides: Partial<CategoryView> & { name: string }): CategoryView {
  const category: CategoryView = {
    id: crypto.randomUUID(),
    kind: 'EXPENSE',
    icon: null,
    color: null,
    parentId: null,
    archivedAt: null,
    isFixed: false,
    ...overrides,
  }
  mockState.categories.push(category)
  return category
}

function find(name: string): CategoryView {
  return mockState.categories.find((category) => category.name === name)!
}

function renderCategories() {
  return renderWithProviders(<CategoriesPage />, { path: '/mais/categorias' })
}

const row = (name: string) => screen.findByRole('button', { name: new RegExp(name) })
const toasts = () => within(screen.getByRole('region', { name: 'Notificações', hidden: true }))

/** Records the JSON body of the next request, letting the mock API answer it. */
function capture(method: 'post' | 'patch', path: string) {
  const captured: { body?: unknown } = {}
  server.use(
    http[method](`${API_URL}/v1/workspaces/:ws/categories${path}`, async ({ request }) => {
      captured.body = await request.clone().json()
    }),
  )
  return captured
}

describe('CategoriesPage', () => {
  it('tabsSplitByKind', async () => {
    seedMockFamily()
    renderCategories()

    expect(await row('Mercado')).toBeInTheDocument()
    expect(screen.queryByText('Salário')).not.toBeInTheDocument()
    expect(document.title).toBe('Categorias · Liberdade')

    fireEvent.click(screen.getByRole('tab', { name: 'Receitas' }))

    expect(await row('Salário')).toBeInTheDocument()
    expect(screen.queryByText('Mercado')).not.toBeInTheDocument()
  })

  it('moves between the tabs with the arrow keys', async () => {
    seedMockFamily()
    renderCategories()
    await row('Mercado')

    const expense = screen.getByRole('tab', { name: 'Gastos' })
    expect(expense).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Gastos')
    fireEvent.keyDown(expense, { key: 'ArrowRight' })

    const income = screen.getByRole('tab', { name: 'Receitas' })
    expect(income).toHaveAttribute('aria-selected', 'true')
    expect(income).toHaveFocus()
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Receitas')
  })

  it('createsSubcategory', async () => {
    seedMockFamily()
    renderCategories()
    const captured = capture('post', '')

    fireEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova categoria' })
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Feira' },
    })
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Mercado' }))
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Verde' }))
    fireEvent.change(within(sheet).getByRole('combobox', { name: 'Dentro de' }), {
      target: { value: find('Mercado').id },
    })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await toasts().findByText('Categoria criada.')).toBeInTheDocument()
    expect(captured.body).toEqual({
      name: 'Feira',
      kind: 'EXPENSE',
      icon: '🛒',
      color: 'GREEN',
      parentId: find('Mercado').id,
    })
    expect(await screen.findByRole('button', { name: /Feira/ })).toBeInTheDocument()
  })

  it('parentOptionsAreTopLevelSameKind', async () => {
    seedMockFamily()
    addCategory({ name: 'Feira', parentId: find('Mercado').id })
    addCategory({ name: 'Antiga', archivedAt: '2026-10-01T10:00:00Z' })
    renderCategories()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova categoria' })
    const options = within(within(sheet).getByRole('combobox', { name: 'Dentro de' }))
      .getAllByRole('option')
      .map((option) => option.textContent)

    expect(options[0]).toBe('Nenhuma')
    expect(options).toContain('Mercado')
    expect(options).not.toContain('Feira')
    expect(options).not.toContain('Antiga')
    expect(options).not.toContain('Salário')
    expect(options).toHaveLength(14)
  })

  it('editKeepsKindAndParent', async () => {
    seedMockFamily()
    addCategory({ name: 'Feira', parentId: find('Mercado').id })
    renderCategories()
    const captured = capture('patch', '/:id')

    fireEvent.click(await row('Feira'))
    const sheet = await screen.findByRole('dialog', { name: 'Feira' })
    expect(within(sheet).getByText('Mercado')).toBeInTheDocument()
    expect(within(sheet).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(sheet).getByRole('button', { name: 'Salvar' })).toBeDisabled()
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Feira livre' },
    })
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Casa' }))
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Azul' }))
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await toasts().findByText('Categoria salva.')).toBeInTheDocument()
    expect(captured.body).toEqual({ name: 'Feira livre', icon: '🏠', color: 'BLUE' })
    expect(find('Feira livre').parentId).toBe(find('Mercado').id)
  })

  it('archiveCountsChildren', async () => {
    seedMockFamily()
    addCategory({ name: 'Feira', parentId: find('Mercado').id })
    addCategory({ name: 'Padaria', parentId: find('Mercado').id })
    addCategory({ name: 'Velha', parentId: find('Mercado').id, archivedAt: '2026-10-01T10:00:00Z' })
    renderCategories()

    fireEvent.click(await row('Mercado'))
    const sheet = await screen.findByRole('dialog', { name: 'Mercado' })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Arquivar' }))

    const dialog = await screen.findByRole('alertdialog', { name: 'Arquivar Mercado?' })
    expect(dialog).toHaveTextContent('As 2 subcategorias também serão arquivadas.')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Arquivar' }))

    expect(await toasts().findByText('Categoria arquivada.')).toBeInTheDocument()
    expect(find('Mercado').archivedAt).not.toBeNull()
    expect(find('Feira').archivedAt).not.toBeNull()
  })

  it('archives a category with no children without the subcategory sentence', async () => {
    seedMockFamily()
    renderCategories()

    fireEvent.click(await row('Mercado'))
    const sheet = await screen.findByRole('dialog', { name: 'Mercado' })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Arquivar' }))

    const dialog = await screen.findByRole('alertdialog', { name: 'Arquivar Mercado?' })
    expect(dialog).toHaveTextContent(
      'Os lançamentos continuam no histórico, e você pode reativar depois.',
    )
    expect(dialog).not.toHaveTextContent('subcategoria')
  })

  it('archivedParentCarriesChildren', async () => {
    seedMockFamily()
    const archivedAt = '2026-10-01T10:00:00Z'
    find('Mercado').archivedAt = archivedAt
    addCategory({ name: 'Feira', parentId: find('Mercado').id, archivedAt })
    renderCategories()

    const archived = (await screen.findByText('Arquivadas (1)')).closest('details')!
    const buttons = within(archived).getAllByRole('button', { name: 'Reativar', hidden: true })
    expect(buttons).toHaveLength(1)
    expect(within(archived).getByText(/Mercado/)).toBeInTheDocument()
    expect(within(archived).getByText(/Feira/)).toBeInTheDocument()

    fireEvent.click(buttons[0])

    expect(await toasts().findByText('Categoria reativada.')).toBeInTheDocument()
    expect(find('Feira').archivedAt).toBeNull()
    expect(await row('Feira')).toBeInTheDocument()
  })

  it('gives an archived child of an active parent its own Reativar', async () => {
    seedMockFamily()
    addCategory({ name: 'Feira', parentId: find('Mercado').id, archivedAt: '2026-10-01T10:00:00Z' })
    renderCategories()

    const archived = (await screen.findByText('Arquivadas (1)')).closest('details')!
    expect(
      within(archived).getAllByRole('button', { name: 'Reativar', hidden: true }),
    ).toHaveLength(1)
    expect(within(archived).getByText(/Feira/)).toBeInTheDocument()
  })

  it('lastActiveExplains', async () => {
    seedMockFamily()
    mockState.categories = mockState.categories.filter(
      (category) => category.kind !== 'EXPENSE' || category.name === 'Mercado',
    )
    renderCategories()

    fireEvent.click(await row('Mercado'))
    const sheet = await screen.findByRole('dialog', { name: 'Mercado' })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Arquivar' }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Arquivar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'É preciso manter pelo menos uma categoria ativa desse tipo.',
    )
    expect(screen.getByRole('dialog', { name: 'Mercado' })).toBeInTheDocument()
  })

  it('nameTakenOnField', async () => {
    seedMockFamily()
    renderCategories()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova categoria' })
    const name = within(sheet).getByRole('textbox', { name: 'Nome' })
    fireEvent.change(name, { target: { value: 'Mercado' } })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    await waitFor(() =>
      expect(name).toHaveAccessibleDescription('Já existe uma categoria com esse nome aqui.'),
    )
    expect(name).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows the API detail of other save errors in the sheet', async () => {
    seedMockFamily()
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/categories`, () =>
        HttpResponse.json(
          { status: 422, code: 'category.parent-archived', detail: 'Reative a principal.' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderCategories()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova categoria' })
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Outra' },
    })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await within(sheet).findByRole('alert')).toHaveTextContent('Reative a principal.')
  })

  it('offlineDisablesWrites', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    addCategory({ name: 'Antiga', archivedAt: '2026-10-01T10:00:00Z' })
    renderCategories()

    expect(await screen.findByRole('button', { name: 'Nova categoria' })).toBeDisabled()
    expect(screen.getByText('Sem conexão. Mudar categorias volta com o sinal.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Reativar', hidden: true })).toBeDisabled()
    fireEvent.click(await row('Mercado'))
    const sheet = await screen.findByRole('dialog', { name: 'Mercado' })
    expect(within(sheet).getByRole('button', { name: 'Arquivar' })).toBeDisabled()
    expect(within(sheet).getByRole('button', { name: 'Salvar' })).toBeDisabled()
  })

  describe('Tags tab', () => {
    it('is the third tab and the arrows reach it', async () => {
      seedMockFamily()
      renderCategories()
      await row('Mercado')

      const income = screen.getByRole('tab', { name: 'Receitas' })
      fireEvent.keyDown(income, { key: 'ArrowRight' })

      const tags = screen.getByRole('tab', { name: 'Tags' })
      expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
        'Gastos',
        'Receitas',
        'Tags',
      ])
      expect(tags).toHaveAttribute('aria-selected', 'true')
      expect(tags).toHaveFocus()
      expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Tags')

      fireEvent.keyDown(tags, { key: 'ArrowRight' })
      expect(screen.getByRole('tab', { name: 'Gastos' })).toHaveAttribute('aria-selected', 'true')
    })

    it('shows the tags list instead of the category tree', async () => {
      seedMockFamily()
      mockState.tags = [{ id: crypto.randomUUID(), name: 'viagem', archivedAt: null }]
      renderCategories()
      await row('Mercado')

      fireEvent.click(screen.getByRole('tab', { name: 'Tags' }))

      expect(await screen.findByText('viagem')).toBeInTheDocument()
      expect(screen.queryByText('Mercado')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Nova categoria' })).not.toBeInTheDocument()
    })
  })
})
