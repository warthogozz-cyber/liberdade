import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { prdExampleVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { venturesKeys } from '@/shared/api/ventures'
import { renderWithProviders } from '@/shared/testing'
import { VenturePage } from './VenturePage'

afterEach(cleanup)

function render(role?: 'VIEWER', status: 'PLANNING' | 'ACTIVE' = 'PLANNING') {
  const workspace = seedMockFamily()
  if (role) workspace.role = role
  const venture = prdExampleVenture({ status })
  seedVentureFixture(workspace, venture)
  renderWithProviders(<VenturePage />, {
    path: '/mais/projetos/:id',
    route: `/mais/projetos/${venture.id}`,
  })
  return venture
}

describe('VenturePage', () => {
  it('showsTheWireframeFigures', async () => {
    const venture = render()

    expect(await screen.findByRole('heading', { level: 1, name: venture.name })).toBeVisible()
    expect(screen.getByText('Planejando')).toBeVisible()
    const figures = screen.getByRole('region', { name: 'Prévia das contas' })
    expect(within(figures).getByText('R$ 2,70')).toBeVisible()
    expect(within(figures).getByText('60,0%')).toBeVisible()
    expect(within(figures).getByText('100 unidades')).toBeVisible()
    expect(within(figures).getByText('R$ 450,00')).toBeVisible()
    expect(within(figures).getByText('R$ 540,00')).toBeVisible()
    expect(within(figures).getByText('2 meses')).toBeVisible()
    expect(within(figures).getByText('710,0%')).toBeVisible()
    expect(figures).not.toHaveAttribute('aria-live')
    expect(screen.getByText('R$ 4,50')).toBeVisible()
    expect(screen.getByText('R$ 1,80')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Editar' })).toHaveAttribute(
      'href',
      `/mais/projetos/${venture.id}/editar`,
    )
    expect(screen.getByRole('button', { name: 'Mudar o status' })).toBeVisible()
    expect(screen.getByRole('checkbox', { name: 'Testar a receita' })).toBeVisible()
    expect(screen.queryByText('Real vs. planejado')).not.toBeInTheDocument()
  })

  it('linkOutsidePlanning', async () => {
    const venture = render(undefined, 'ACTIVE')

    expect(await screen.findByRole('link', { name: 'Real vs. planejado ›' })).toHaveAttribute(
      'href',
      `/mais/projetos/${venture.id}/real`,
    )
  })

  it('noLinkWhilePlanning', async () => {
    render()

    expect(await screen.findByText('Planejando')).toBeVisible()
    expect(screen.queryByRole('link', { name: /Real vs\. planejado/ })).not.toBeInTheDocument()
  })

  it('viewerSeesNoControls', async () => {
    render('VIEWER')

    expect(await screen.findByText('Planejando')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Editar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mudar o status' })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.getByText('Testar a receita')).toBeVisible()
  })

  it('showsAGoneProjectWithALinkBack', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace, prdExampleVenture())
    renderWithProviders(<VenturePage />, {
      path: '/mais/projetos/:id',
      route: '/mais/projetos/0192f3a4-0000-7000-8000-0000000000ff',
    })

    expect(await screen.findByText('Este projeto não existe mais.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Voltar aos projetos' })).toHaveAttribute(
      'href',
      '/mais/projetos',
    )
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('showsTheGoneNoticeWhenTheProjectIsDeletedElsewhere', async () => {
    const workspace = seedMockFamily()
    const venture = prdExampleVenture()
    seedVentureFixture(workspace, venture)
    const { queryClient } = renderWithProviders(<VenturePage />, {
      path: '/mais/projetos/:id',
      route: `/mais/projetos/${venture.id}`,
    })
    expect(await screen.findByRole('heading', { level: 1, name: venture.name })).toBeVisible()

    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/ventures/:id`, () =>
        problem(404, 'venture.not-found', 'Não achei.'),
      ),
    )
    void queryClient.invalidateQueries({ queryKey: venturesKeys.all(workspace.id) })

    expect(await screen.findByText('Este projeto não existe mais.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Voltar aos projetos' })).toHaveAttribute(
      'href',
      '/mais/projetos',
    )
    await waitFor(() => expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument())
  })
})
