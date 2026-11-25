import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, prdExampleVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import type { VentureView } from '@/shared/api/ventures'
import { renderWithProviders } from '@/shared/testing'
import { StatusSheet } from './StatusSheet'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-25T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

function render(overrides: Partial<VentureView> = {}) {
  const workspace = seedMockFamily()
  const venture = prdExampleVenture(overrides)
  seedVentureFixture(workspace, venture)
  const onOpenChange = vi.fn()
  const view = renderWithProviders(null, {
    routes: [
      {
        path: '/mais/projetos/:id',
        element: (
          <StatusSheet workspace={workspace} venture={venture} open onOpenChange={onOpenChange} />
        ),
      },
      { path: '/mais/projetos', element: <p>A lista</p> },
    ],
    route: `/mais/projetos/${venture.id}`,
  })
  return { ...view, workspace, venture, onOpenChange }
}

describe('StatusSheet', () => {
  it('onlyAllowedTransitions', async () => {
    const planning = render()
    const sheet = await screen.findByRole('dialog', { name: 'Status do projeto' })
    expect(within(sheet).getByRole('button', { name: 'Começar a vender' })).toBeVisible()
    expect(within(sheet).getByRole('button', { name: 'Encerrar' })).toBeVisible()
    expect(within(sheet).queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument()
    expect(within(sheet).queryByRole('button', { name: 'Reabrir' })).not.toBeInTheDocument()
    planning.unmount()

    render({ status: 'CLOSED', closedOn: '2026-11-01' })
    const closed = await screen.findByRole('dialog', { name: 'Status do projeto' })
    expect(within(closed).getByRole('button', { name: 'Reabrir' })).toBeVisible()
    expect(within(closed).queryByRole('button', { name: 'Encerrar' })).not.toBeInTheDocument()
  })

  it('startAsksForTheDateAndSendsIt', async () => {
    const bodies: unknown[] = []
    const { venture } = render()
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/:id/status`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ ...venture, status: 'ACTIVE', version: 1 })
      }),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Começar a vender' }))
    const date = screen.getByLabelText('Data de início')
    expect(date).toHaveValue('2026-11-25')
    expect(date).toHaveAttribute('max', '2026-11-25')
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    await waitFor(() =>
      expect(bodies).toEqual([{ status: 'ACTIVE', on: '2026-11-25', version: venture.version }]),
    )
  })

  it('pauseSendsNoDate', async () => {
    const bodies: unknown[] = []
    const { venture, onOpenChange } = render({ status: 'ACTIVE', startedOn: '2026-11-01' })
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/:id/status`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ ...venture, status: 'PAUSED', version: 1 })
      }),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Pausar' }))

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(bodies).toEqual([{ status: 'PAUSED', version: venture.version }])
  })

  it('limitOnReopen', async () => {
    render({ status: 'CLOSED', closedOn: '2026-11-01' })
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/:id/status`, () =>
        problem(403, 'venture.limit-reached', 'No plano Grátis, a família tem 1 projeto ativo.'),
      ),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Reabrir' }))

    expect(
      await screen.findByText(/No plano Grátis, a família tem 1 projeto ativo\. Encerre um/),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Mais > Plano' })).toHaveAttribute(
      'href',
      '/mais/plano',
    )
    expect(screen.getByRole('dialog', { name: 'Status do projeto' })).toBeVisible()
  })

  it('staleVersionShowsTheConflictAndKeepsTheSheetOpen', async () => {
    const { onOpenChange } = render({ status: 'ACTIVE', startedOn: '2026-11-01' })
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/:id/status`, () =>
        problem(409, 'venture.version-conflict', 'Outra pessoa alterou.'),
      ),
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Pausar' }))

    expect(await screen.findByText(/Outra pessoa alterou este projeto/)).toBeVisible()
    expect(onOpenChange).not.toHaveBeenCalledWith(false)
  })

  it('deleteAsksFirst', async () => {
    let deletes = 0
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'DELETE') deletes += 1
    })
    render()

    fireEvent.click(await screen.findByRole('button', { name: 'Excluir projeto' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('Excluir o projeto?')).toBeVisible()
    expect(
      within(dialog).getByText('Itens, produtos e passos somem. Não dá para desfazer.'),
    ).toBeVisible()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(deletes).toBe(0)
  })

  it('deleteGoesToTheListWithoutReadingTheDeletedProject', async () => {
    const events: string[] = []
    server.events.on('request:start', ({ request }) => {
      const path = new URL(request.url).pathname
      if (request.method === 'DELETE') events.push('DELETE')
      if (request.method === 'GET' && /\/ventures\/[^/]+$/.test(path)) events.push('GET detail')
    })
    const { venture, workspace } = render()

    fireEvent.click(await screen.findByRole('button', { name: 'Excluir projeto' }))
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Excluir' }))

    expect(await screen.findByText('A lista')).toBeVisible()
    expect(mockState.ventures[workspace.id]?.map((each) => each.id)).not.toContain(venture.id)
    expect(events.slice(events.indexOf('DELETE'))).toEqual(['DELETE'])
  })

  it('cancellingTheDeleteDialogReturnsFocusToTheDeleteButton', async () => {
    render()

    const trigger = await screen.findByRole('button', { name: 'Excluir projeto' })
    trigger.focus()
    fireEvent.click(trigger)
    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    await waitFor(() => expect(trigger).toHaveFocus())
  })

  it('reopeningStartsClean', async () => {
    const workspace = seedMockFamily()
    const venture = prdExampleVenture({ status: 'CLOSED', closedOn: '2026-11-01' })
    seedVentureFixture(workspace, venture)
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/:id/status`, () =>
        problem(409, 'venture.version-conflict', 'Outra pessoa alterou.'),
      ),
    )
    function Harness() {
      const [open, setOpen] = useState(true)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            Abrir de novo
          </button>
          <StatusSheet workspace={workspace} venture={venture} open={open} onOpenChange={setOpen} />
        </>
      )
    }
    renderWithProviders(null, {
      routes: [{ path: '/mais/projetos/:id', element: <Harness /> }],
      route: `/mais/projetos/${venture.id}`,
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Reabrir' }))
    expect(await screen.findByText(/Outra pessoa alterou este projeto/)).toBeVisible()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Abrir de novo' }))
    expect(await screen.findByRole('dialog', { name: 'Status do projeto' })).toBeVisible()
    expect(screen.queryByText(/Outra pessoa alterou este projeto/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Data de início')).not.toBeInTheDocument()
  })
})
