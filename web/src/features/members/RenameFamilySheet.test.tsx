import { cleanup, fireEvent, screen } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { RenameFamilySheet } from './RenameFamilySheet'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderSheet(onClose: () => void = () => {}) {
  const workspace = seedMockFamily()
  renderWithProviders(<RenameFamilySheet workspace={workspace} onClose={onClose} />)
  return workspace
}

describe('RenameFamilySheet', () => {
  it('renamesAndSaysSo', async () => {
    const onClose = vi.fn()
    const workspace = renderSheet(onClose)

    fireEvent.change(screen.getByLabelText('Nome da família'), {
      target: { value: 'Família Bia Teste' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Nome da família atualizado.')).toBeInTheDocument()
    expect(workspace.name).toBe('Família Bia Teste')
    expect(onClose).toHaveBeenCalled()
  })

  it('showsTheServersReasonOnFailure', async () => {
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws`, () =>
        problem(422, 'workspace.name-invalid', 'Esse nome não serve.'),
      ),
    )
    const onClose = vi.fn()
    renderSheet(onClose)

    fireEvent.change(screen.getByLabelText('Nome da família'), { target: { value: 'X' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Esse nome não serve.')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })
})
