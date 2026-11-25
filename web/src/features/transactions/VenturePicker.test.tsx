import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { queryKeys } from '@/shared/api'
import { venturesKeys } from '@/shared/api/ventures'
import { createTestQueryClient, renderWithProviders } from '@/shared/testing'
import { VenturePicker } from './VenturePicker'

afterEach(cleanup)

const OPEN_ID = '0192f3a4-0000-7000-8000-0000000000a1'
const CLOSED_ID = '0192f3a4-0000-7000-8000-0000000000a2'

async function renderPicker(keep: string | null) {
  const onChange = vi.fn()
  const workspace = seedMockFamily()
  seedVentureFixture(workspace)
  seedVentureFixture(workspace, {
    id: CLOSED_ID,
    name: 'Bolos antigos',
    status: 'CLOSED',
    closedOn: '2026-10-20',
  })
  const queryClient = createTestQueryClient()
  renderWithProviders(<VenturePicker value={keep} onChange={onChange} keep={keep} />, {
    queryClient,
  })
  await waitFor(() =>
    expect(queryClient.getQueryState(venturesKeys.list(workspace.id))?.status).toBe('success'),
  )
  await waitFor(() =>
    expect(queryClient.getQueryState(queryKeys.modules(workspace.id))?.status).toBe('success'),
  )
  return onChange
}

describe('VenturePicker', () => {
  it('keepsAClosedVenture', async () => {
    await renderPicker(CLOSED_ID)

    expect(await screen.findByRole('option', { name: 'Doces caseiros' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Bolos antigos (encerrado)' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Projeto (opcional)' })).toHaveValue(CLOSED_ID)
  })

  it('offersOnlyOpenVenturesOtherwise', async () => {
    const onChange = await renderPicker(null)

    const field = await screen.findByRole('combobox', { name: 'Projeto (opcional)' })
    expect(screen.queryByRole('option', { name: /Bolos antigos/ })).not.toBeInTheDocument()
    fireEvent.change(field, { target: { value: OPEN_ID } })
    expect(onChange).toHaveBeenCalledWith(OPEN_ID)
    fireEvent.change(field, { target: { value: '' } })
    expect(onChange).toHaveBeenLastCalledWith(null)
  })
})
