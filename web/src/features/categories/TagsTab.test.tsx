import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type MeWorkspace } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import TagsTab from './TagsTab'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function seedTags(role: MeWorkspace['role'] = 'OWNER'): MeWorkspace {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.tags = [
    { id: '0192f3a4-0000-7000-8000-0000000000d2', name: 'viagem', archivedAt: null },
    { id: '0192f3a4-0000-7000-8000-0000000000d1', name: 'Árvore', archivedAt: null },
    {
      id: '0192f3a4-0000-7000-8000-0000000000d3',
      name: 'antiga',
      archivedAt: '2026-01-01T00:00:00Z',
    },
  ]
  return workspace
}

function renderTags(workspace: MeWorkspace) {
  return renderWithProviders(<TagsTab workspace={workspace} />, { path: '/mais/categorias' })
}

describe('TagsTab', () => {
  it('lists the active tags in alphabetical order', async () => {
    renderTags(seedTags())

    const items = await screen.findAllByRole('listitem')

    expect(items.map((item) => within(item).getByText(/^(Árvore|viagem)$/).textContent)).toEqual([
      'Árvore',
      'viagem',
    ])
    expect(screen.queryByText('antiga')).not.toBeInTheDocument()
  })

  it('shows a note when there are no tags', async () => {
    const workspace = seedTags()
    mockState.tags = []
    renderTags(workspace)

    expect(await screen.findByText(/Nenhuma tag ainda/)).toBeInTheDocument()
  })

  it('lets an Owner rename a tag, sending the trimmed name', async () => {
    const workspace = seedTags()
    let body: unknown
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/tags/:id`, async ({ request }) => {
        body = await request.clone().json()
      }),
    )
    renderTags(workspace)

    fireEvent.click((await screen.findAllByRole('button', { name: /Renomear/ }))[1])
    const field = await screen.findByLabelText('Nome')
    expect(field).toHaveValue('viagem')
    fireEvent.change(field, { target: { value: '  férias  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(body).toEqual({ name: 'férias' }))
    await waitFor(() => expect(screen.queryByLabelText('Nome')).not.toBeInTheDocument())
    expect(mockState.tags.find((tag) => tag.id.endsWith('d2'))?.name).toBe('férias')
  })

  it('keeps the sheet open and says so when the name is taken', async () => {
    const workspace = seedTags()
    renderTags(workspace)

    fireEvent.click((await screen.findAllByRole('button', { name: /Renomear/ }))[1])
    fireEvent.change(await screen.findByLabelText('Nome'), { target: { value: 'árvore' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Já existe uma tag com esse nome.')).toBeInTheDocument()
    expect(screen.getByRole('textbox')).toHaveAccessibleDescription(
      'Já existe uma tag com esse nome.',
    )
  })

  it('asks before archiving, and archives on confirm', async () => {
    const workspace = seedTags()
    renderTags(workspace)

    fireEvent.click((await screen.findAllByRole('button', { name: /Arquivar/ }))[0])
    expect(
      await screen.findByText('As tags arquivadas continuam nos lançamentos antigos.'),
    ).toBeInTheDocument()
    expect(mockState.tags.find((tag) => tag.id.endsWith('d1'))?.archivedAt).toBeNull()

    const dialog = screen.getByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Arquivar' }))

    await waitFor(() =>
      expect(mockState.tags.find((tag) => tag.id.endsWith('d1'))?.archivedAt).not.toBeNull(),
    )
    await waitFor(() => expect(screen.queryByText('Árvore')).not.toBeInTheDocument())
  })

  it('does not archive when the confirmation is cancelled', async () => {
    renderTags(seedTags())

    fireEvent.click((await screen.findAllByRole('button', { name: /Arquivar/ }))[0])
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(mockState.tags.find((tag) => tag.id.endsWith('d1'))?.archivedAt).toBeNull()
  })

  it.each(['VIEWER', 'YOUTH'] as const)('shows %s the list without actions', async (role) => {
    renderTags(seedTags(role))

    expect(await screen.findByText('Árvore')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Renomear/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Arquivar/ })).not.toBeInTheDocument()
  })

  it('lets an Adult rename', async () => {
    renderTags(seedTags('ADULT'))

    expect((await screen.findAllByRole('button', { name: /Renomear/ })).length).toBe(2)
  })
})
