import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { PasskeyList } from './PasskeyList'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('PasskeyList', () => {
  it('showsPasskeys', async () => {
    seedMockFamily()
    mockState.passkeys = [
      {
        id: 'p1',
        label: 'Notebook',
        createdAt: '2026-11-10T12:00:00Z',
        lastUsedAt: '2026-11-14T12:00:00Z',
      },
      { id: 'p2', label: 'Celular', createdAt: '2026-10-01T12:00:00Z', lastUsedAt: null },
    ]
    renderWithProviders(<PasskeyList />)

    const items = await screen.findAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]!).getByText('Notebook')).toBeInTheDocument()
    expect(within(items[0]!).getByText('Criada em 10/11/2026')).toBeInTheDocument()
    expect(within(items[0]!).getByText('Último uso em 14/11/2026')).toBeInTheDocument()
    expect(within(items[1]!).getByText('Criada em 01/10/2026')).toBeInTheDocument()
    expect(within(items[1]!).getByText('Nunca usada')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remover passkey Notebook' })).toBeEnabled()
  })

  it('empty', async () => {
    seedMockFamily()
    mockState.passkeys = []
    renderWithProviders(<PasskeyList />)

    expect(await screen.findByText('Nenhuma passkey neste perfil')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })
})
