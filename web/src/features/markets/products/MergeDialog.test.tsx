import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { MergeDialog } from './MergeDialog'

afterEach(cleanup)

const A = { id: '00000000-0000-4000-8000-00000000b001', name: 'Café Exemplo 500 g' }
const B = { id: '00000000-0000-4000-8000-00000000b002', name: 'Café Exemplo Torrado 500 g' }
const MERGE = `${API_URL}/v1/workspaces/:ws/markets/products/:keepId/merge`

function open() {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  const onClose = vi.fn()
  const onMerged = vi.fn()
  renderWithProviders(
    <MergeDialog ws={workspace.id} a={A} b={B} onClose={onClose} onMerged={onMerged} />,
    { path: '/mais/feira/produtos', route: '/mais/feira/produtos' },
  )
  return { onClose, onMerged }
}

describe('MergeDialog', () => {
  it('warnsAndChoosesName', async () => {
    let call: { keepId: string; body: unknown } | undefined
    server.use(
      http.post(MERGE, async ({ request, params }) => {
        call = { keepId: params.keepId as string, body: await request.json() }
        return HttpResponse.json({ id: B.id, name: B.name, unit: 'UN', gtin: null, prices: [] })
      }),
    )
    const { onMerged } = open()

    expect(
      await screen.findByText(
        'Os preços de Café Exemplo Torrado 500 g passam para Café Exemplo 500 g, e Café Exemplo Torrado 500 g some. Não dá para desfazer.',
      ),
    ).toBeVisible()
    fireEvent.click(screen.getByRole('radio', { name: B.name }))
    expect(
      screen.getByText(
        'Os preços de Café Exemplo 500 g passam para Café Exemplo Torrado 500 g, e Café Exemplo 500 g some. Não dá para desfazer.',
      ),
    ).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Juntar' }))

    await waitFor(() => expect(onMerged).toHaveBeenCalled())
    expect(call).toEqual({ keepId: B.id, body: { removeId: A.id } })
  })

  it('gtinConflictMessage', async () => {
    server.use(
      http.post(MERGE, () =>
        HttpResponse.json(
          { status: 422, code: 'product.merge_gtin_conflict', detail: 'detalhe do servidor' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const { onMerged } = open()

    fireEvent.click(await screen.findByRole('button', { name: 'Juntar' }))

    expect(
      await screen.findByText(
        'Estes produtos têm códigos de barras diferentes e não podem ser juntados.',
      ),
    ).toBeVisible()
    expect(onMerged).not.toHaveBeenCalled()
  })
})
