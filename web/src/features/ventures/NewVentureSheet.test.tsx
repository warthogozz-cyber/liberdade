import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { problem } from '@/mocks/handlers'
import { seedMockFamily, seedVentureFixture, ventureTracks } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { NewVentureSheet } from './NewVentureSheet'

beforeEach(() => {
  localStorage.setItem('liberdade.tips', 'on')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
})

describe('NewVentureSheet', () => {
  it('createsFromTemplateAndOpensTheForm', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    let received: unknown
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures`, async ({ request }) => {
        received = await request.json()
        return HttpResponse.json({ id: '0192f3a4-0000-7000-8000-0000000000e1' }, { status: 201 })
      }),
    )
    const template = ventureTracks()[0]!.templates[0]!
    const { router } = renderWithProviders(
      <NewVentureSheet workspaceId={workspace.id} template={template} onClose={() => {}} />,
      { path: '/mais/projetos/comecar' },
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Criar projeto' }))

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(
        '/mais/projetos/0192f3a4-0000-7000-8000-0000000000e1/editar',
      ),
    )
    expect(received).toEqual({ name: 'Doces caseiros', templateKey: 'food-sweets' })
  })

  it('blankAsksForNameAndType', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    let received: unknown
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures`, async ({ request }) => {
        received = await request.json()
        return HttpResponse.json({ id: '0192f3a4-0000-7000-8000-0000000000e2' }, { status: 201 })
      }),
    )
    renderWithProviders(<NewVentureSheet workspaceId={workspace.id} onClose={() => {}} />, {
      path: '/mais/projetos/comecar',
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Criar projeto' }))
    expect(await screen.findByText('Informe um nome de até 40 caracteres.')).toBeVisible()
    expect(received).toBeUndefined()

    fireEvent.change(screen.getByLabelText('Nome do projeto'), { target: { value: 'Bolsas' } })
    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'RESALE' } })
    fireEvent.click(screen.getByRole('button', { name: 'Criar projeto' }))

    await waitFor(() => expect(received).toEqual({ name: 'Bolsas', type: 'RESALE' }))
  })

  it('linksOnlyTheNameErrorToTheNameField', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures`, () =>
        problem(403, 'venture.limit-reached', 'No plano Grátis, a família tem 1 projeto ativo.'),
      ),
    )
    renderWithProviders(<NewVentureSheet workspaceId={workspace.id} onClose={() => {}} />, {
      path: '/mais/projetos/comecar',
    })
    const input = await screen.findByLabelText('Nome do projeto')

    fireEvent.click(screen.getByRole('button', { name: 'Criar projeto' }))
    await screen.findByText('Informe um nome de até 40 caracteres.')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'new-venture-error')

    fireEvent.change(input, { target: { value: 'Bolsas' } })
    fireEvent.click(screen.getByRole('button', { name: 'Criar projeto' }))
    await screen.findByText('No plano Grátis, a família tem 1 projeto ativo.')
    expect(input).toHaveAttribute('aria-invalid', 'false')
    expect(input).not.toHaveAttribute('aria-describedby')
  })
})
