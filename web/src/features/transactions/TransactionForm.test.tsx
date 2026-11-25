import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { queryKeys } from '@/shared/api'
import { venturesKeys } from '@/shared/api/ventures'
import { db } from '@/shared/offline/db'
import { createTestQueryClient, renderWithProviders } from '@/shared/testing'
import { NewTransactionPage } from './NewTransactionPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

/** Renders the new-transaction form and waits for the categories. */
async function openForm() {
  const rendered = renderWithProviders(<NewTransactionPage />, { path: '/lancar' })
  await screen.findByRole('radio', { name: '🛒 Mercado' })
  return rendered
}

const press = (...keys: string[]) => {
  for (const key of keys) fireEvent.click(screen.getByRole('button', { name: key }))
}
const saveButton = () => screen.getByRole('button', { name: 'Salvar' })
const methodGroup = () => screen.queryByRole('group', { name: 'Forma de pagamento' })

describe('TransactionForm payment method and tags', () => {
  it('sends the chosen method and tag with an expense, and the queue keeps both', async () => {
    seedMockFamily()
    mockState.tags.push({ id: crypto.randomUUID(), name: 'Escola', archivedAt: null })
    const { router } = await openForm()

    press('1', '2', '3', '4')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(within(methodGroup()!).getByRole('button', { name: 'Pix' }))
    const field = screen.getByRole('combobox', { name: 'Tags' })
    fireEvent.change(field, { target: { value: 'esc' } })
    fireEvent.click(await screen.findByRole('option', { name: 'Escola' }))
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const items = await db.outbox.toArray()
    expect(items).toHaveLength(1)
    expect(items[0].payload).toMatchObject({ paymentMethod: 'PIX', tags: ['Escola'] })
  })

  it('keeps a tag that was typed but not confirmed when Salvar is pressed', async () => {
    seedMockFamily()
    const { router } = await openForm()

    press('5')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    const field = screen.getByRole('combobox', { name: 'Tags' })
    fireEvent.change(field, { target: { value: 'Escola' } })
    // Tapping the button takes focus from the field first.
    fireEvent.blur(field)
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const [item] = await db.outbox.toArray()
    expect(item.payload).toMatchObject({ tags: ['Escola'] })
  })

  it('always sends the fields, empty when nothing was chosen', async () => {
    seedMockFamily()
    const { router } = await openForm()

    press('5')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const [item] = await db.outbox.toArray()
    expect(item.payload).toMatchObject({ paymentMethod: null, tags: [] })
  })

  it('hides the chips on a transfer and sends no method', async () => {
    const workspace = seedMockFamily()
    mockState.accounts.push({
      ...mockState.accounts[0],
      id: crypto.randomUUID(),
      name: 'Banco',
      ownerMembershipId: workspace.membershipId,
    })
    const { router } = await openForm()

    fireEvent.click(within(methodGroup()!).getByRole('button', { name: 'Pix' }))
    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    expect(methodGroup()).not.toBeInTheDocument()

    press('5', '0')
    fireEvent.click(
      within(screen.getByRole('group', { name: 'De' })).getByRole('radio', { name: 'Carteira' }),
    )
    fireEvent.click(
      within(screen.getByRole('group', { name: 'Para' })).getByRole('radio', { name: 'Banco' }),
    )
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const [item] = await db.outbox.toArray()
    expect(item.payload).toMatchObject({ kind: 'TRANSFER', paymentMethod: null })
  })
})

const FIELD = { name: 'Projeto (opcional)' }

/** Renders the form with its own cache, and waits until the module list has answered. */
async function openWithVentures(workspaceId: string, ventures: boolean) {
  const queryClient = createTestQueryClient()
  const rendered = renderWithProviders(<NewTransactionPage />, { path: '/lancar', queryClient })
  await screen.findByRole('radio', { name: '🛒 Mercado' })
  await waitFor(() =>
    expect(queryClient.getQueryState(queryKeys.modules(workspaceId))?.status).toBe('success'),
  )
  if (ventures) {
    await waitFor(() =>
      expect(queryClient.getQueryState(venturesKeys.list(workspaceId))?.status).toBe('success'),
    )
  }
  return rendered
}

describe('TransactionForm project field', () => {
  it('showsTheFieldOnIncomeAndExpense', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    mockState.accounts.push({
      ...mockState.accounts[0],
      id: crypto.randomUUID(),
      name: 'Banco',
      ownerMembershipId: workspace.membershipId,
    })
    await openWithVentures(workspace.id, true)

    expect(await screen.findByRole('combobox', FIELD)).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Nenhum' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Doces caseiros' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'Receita' }))
    expect(screen.getByRole('combobox', FIELD)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    expect(screen.queryByRole('combobox', FIELD)).not.toBeInTheDocument()
  })

  it('hiddenWithoutOpenVentures', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace, { status: 'CLOSED', closedOn: '2026-10-20' })
    await openWithVentures(workspace.id, true)

    expect(screen.queryByRole('combobox', FIELD)).not.toBeInTheDocument()
  })

  it('youthNeverSeesTheField', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    workspace.role = 'YOUTH'
    await openWithVentures(workspace.id, false)

    expect(screen.queryByRole('combobox', FIELD)).not.toBeInTheDocument()
  })

  it('moduleOffHidesTheField', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    mockState.modules[workspace.id] = { ...mockState.modules[workspace.id], ventures: false }
    await openWithVentures(workspace.id, false)

    expect(screen.queryByRole('combobox', FIELD)).not.toBeInTheDocument()
  })

  it('worksOffline', async () => {
    const workspace = seedMockFamily()
    const venture = seedVentureFixture(workspace)
    const { router } = await openWithVentures(workspace.id, true)
    const field = await screen.findByRole('combobox', FIELD)

    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    window.dispatchEvent(new Event('offline'))
    press('5', '0')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.change(field, { target: { value: venture.id } })
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const [item] = await db.outbox.toArray()
    expect(item.payload).toMatchObject({ kind: 'EXPENSE', ventureId: venture.id })
  })

  it('sendsNoProjectWhenNoneIsChosen', async () => {
    const workspace = seedMockFamily()
    seedVentureFixture(workspace)
    const { router } = await openWithVentures(workspace.id, true)
    await screen.findByRole('combobox', FIELD)

    press('5')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const [item] = await db.outbox.toArray()
    expect(item.payload).not.toHaveProperty('ventureId')
  })
})
