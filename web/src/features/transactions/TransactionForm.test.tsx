import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { db } from '@/shared/offline/db'
import { renderWithProviders } from '@/shared/testing'
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
