import { act, cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest'
import type { ExpenseDate } from '@/features/transactions/ContextSheet'
import { NewTransactionPage } from '@/features/transactions/NewTransactionPage'
import { mockState, seedMockFamily, type MockAccount } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, postTransaction, type MeWorkspace } from '@/shared/api'
import { localDate } from '@/shared/i18n'
import { db, syncOutbox } from '@/shared/offline'
import { outboxItem, outboxPayload, renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function renderPage(route = '/lancar') {
  return renderWithProviders(<NewTransactionPage />, { path: '/lancar', route })
}

/** Renders the page and waits for the categories. */
async function openForm(route?: string) {
  const rendered = renderPage(route)
  await screen.findByRole('radio', { name: '🛒 Mercado' })
  return rendered
}

const amount = () => screen.getByRole('status', { name: 'Valor' })
const saveButton = () => screen.getByRole('button', { name: 'Salvar' })
const press = (...keys: string[]) => {
  for (const key of keys) fireEvent.click(screen.getByRole('button', { name: key }))
}
const category = (name: string) => mockState.categories.find((each) => each.name === name)!
const toastRegion = () => screen.getByRole('region', { name: 'Notificações' })
const group = (name: string) => screen.getByRole('group', { name })

/** Adds an account to the mock family, active unless `archivedAt` is given. */
function addAccount(
  workspace: MeWorkspace,
  name: string,
  type: MockAccount['type'] = 'CHECKING',
  archivedAt: string | null = null,
): MockAccount {
  const account: MockAccount = {
    id: crypto.randomUUID(),
    name,
    type,
    currency: 'BRL',
    initialBalance: { amount: '0.00', currency: 'BRL' },
    ownerMembershipId: workspace.membershipId,
    archivedAt,
  }
  mockState.accounts.push(account)
  return account
}

/** Lets the queue's delay pass and sends it, as the app's sync would. Needs a fake Date. */
async function sendQueue() {
  vi.setSystemTime(Date.now() + 7000)
  const result = await syncOutbox({
    userId: mockState.user.id,
    send: (item, signal) => postTransaction(item.workspaceId, item.payload, signal),
    onSent: () => {},
  })
  expect(result.status).toBe('done')
}

/** Types 12,34, picks Mercado and saves, then waits for the home route. */
async function saveExpense(router: { state: { location: { pathname: string } } }) {
  press('1', '2', '3', '4')
  fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
  fireEvent.click(saveButton())
  await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
}

/** Opens the context sheet. */
function openContext() {
  fireEvent.click(screen.getByRole('button', { name: /· Eu$/ }))
  return screen.getByRole('dialog', { name: 'Data e conta' })
}

describe('NewTransactionPage', () => {
  it('opens empty, with the six first categories and today in the wallet', async () => {
    seedMockFamily()
    await openForm()

    expect(screen.getByRole('heading', { level: 1, name: 'Novo gasto' })).toBeInTheDocument()
    expect(amount()).toHaveTextContent('R$ 0,00')
    const categories = within(screen.getByRole('group', { name: 'Categoria' }))
    expect(categories.getAllByRole('radio')).toHaveLength(6)
    expect(
      categories.getAllByRole('radio').filter((radio) => (radio as HTMLInputElement).checked),
    ).toHaveLength(0)
    expect(screen.getByRole('button', { name: 'Mais' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hoje · Carteira · Eu' })).toBeInTheDocument()
    expect(saveButton()).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Voltar' })).toHaveAttribute('href', '/inicio')
    expect(document.title).toBe('Novo gasto · Liberdade')
  })

  it('types the amount on the keypad', async () => {
    seedMockFamily()
    await openForm()

    press('1', '2', '3', '4')
    expect(amount()).toHaveTextContent('R$ 12,34')
    press('00')
    expect(amount()).toHaveTextContent('R$ 1.234,00')
    press('Apagar')
    expect(amount()).toHaveTextContent('R$ 123,40')
  })

  it('takes the digits and Backspace from a physical keyboard', async () => {
    seedMockFamily()
    await openForm()

    for (const key of ['1', '2', '3', '4', 'Backspace']) fireEvent.keyDown(document.body, { key })

    expect(amount()).toHaveTextContent('R$ 1,23')
  })

  it('leaves the amount alone while typing the description', async () => {
    seedMockFamily()
    await openForm()
    const description = screen.getByRole('textbox', { name: 'Descrição (opcional)' })

    fireEvent.keyDown(description, { key: '1' })
    fireEvent.keyDown(description, { key: '2' })
    fireEvent.change(description, { target: { value: '12' } })

    expect(amount()).toHaveTextContent('R$ 0,00')
    expect(description).toHaveAttribute('maxLength', '140')
  })

  it('keeps Salvar disabled without a category or without an amount', async () => {
    seedMockFamily()
    await openForm()

    press('5')
    expect(saveButton()).toBeDisabled()

    press('Apagar')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    expect(saveButton()).toBeDisabled()
  })

  it('queues the expense, goes home and offers Desfazer', async () => {
    const workspace = seedMockFamily()
    const { router } = await openForm()

    press('1', '2', '3', '4')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Descrição (opcional)' }), {
      target: { value: '  pão  ' },
    })
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const items = await db.outbox.toArray()
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({
      userId: mockState.user.id,
      workspaceId: workspace.id,
      status: 'pending',
      payload: {
        id: items[0].id,
        kind: 'EXPENSE',
        amount: { amount: '12.34', currency: 'BRL' },
        categoryId: category('Mercado').id,
        accountId: mockState.accounts[0].id,
        occurredOn: localDate(new Date(), 'America/Fortaleza'),
        description: 'pão',
      },
    })
    expect(items[0].id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/)
    expect(await within(toastRegion()).findByText('Gasto salvo.')).toBeInTheDocument()
    expect(within(toastRegion()).getByRole('button', { name: 'Desfazer' })).toBeInTheDocument()
  })

  it('sends a blank description as null', async () => {
    seedMockFamily()
    const { router } = await openForm()
    fireEvent.change(screen.getByRole('textbox', { name: 'Descrição (opcional)' }), {
      target: { value: '   ' },
    })

    await saveExpense(router)

    expect((await db.outbox.toArray())[0].payload.description).toBeNull()
  })

  it('queues one item for two quick taps on Salvar', async () => {
    seedMockFamily()
    const { router } = await openForm()

    press('1', '2', '3', '4')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    const button = saveButton()
    // Both taps land before React renders, so the button is still enabled for the second one.
    act(() => {
      button.click()
      button.click()
    })

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(await db.outbox.count()).toBe(1)
  })

  it('says the save failed and lets Salvar try again', async () => {
    seedMockFamily()
    const { router } = await openForm()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(db.outbox, 'add').mockRejectedValueOnce(new Error('QuotaExceededError'))

    press('1', '2', '3', '4')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(saveButton())

    expect(
      await within(toastRegion()).findByText('Não foi possível salvar o gasto. Tente de novo.'),
    ).toBeInTheDocument()
    expect(consoleError).toHaveBeenCalled()
    expect(router.state.location.pathname).toBe('/lancar')
    await waitFor(() => expect(saveButton()).toBeEnabled())

    fireEvent.click(saveButton())
    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    expect(await db.outbox.count()).toBe(1)
  })

  it('works out Hoje in the family time zone when saving', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime('2026-11-29T12:00:00Z')
    seedMockFamily()
    const { router } = await openForm()

    // The form stayed open past midnight in Fortaleza: Hoje is the day it is saved.
    vi.setSystemTime('2026-12-01T02:30:00Z')
    await saveExpense(router)

    expect((await db.outbox.toArray())[0].payload.occurredOn).toBe('2026-11-30')
  })

  it('works out Ontem in the family time zone', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime('2026-12-01T02:30:00Z')
    seedMockFamily()
    const { router } = await openForm()

    const sheet = openContext()
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Ontem' }))
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Ontem · Carteira · Eu' })).toBeInTheDocument()
    await saveExpense(router)

    expect((await db.outbox.toArray())[0].payload.occurredOn).toBe('2026-11-29')
  })

  it('saves another date picked in the context sheet', async () => {
    seedMockFamily()
    const { router } = await openForm()

    const sheet = openContext()
    expect(within(sheet).getByText('Quem gastou: Eu')).toBeInTheDocument()
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Outra data' }))
    fireEvent.change(
      within(sheet).getByLabelText('Outra data', { selector: 'input[type="date"]' }),
      {
        target: { value: '2026-10-20' },
      },
    )
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    expect(screen.getByRole('button', { name: '20 de out. · Carteira · Eu' })).toBeInTheDocument()
    await saveExpense(router)
    expect((await db.outbox.toArray())[0].payload.occurredOn).toBe('2026-10-20')
  })

  it('picks a category from Mais, which closes the sheet', async () => {
    seedMockFamily()
    await openForm()

    fireEvent.click(screen.getByRole('button', { name: 'Mais' }))
    const sheet = screen.getByRole('dialog', { name: 'Todas as categorias' })
    expect(within(sheet).getAllByRole('button', { name: /^\S+ \S/ })).toHaveLength(13)
    fireEvent.click(within(sheet).getByRole('button', { name: '🐾 Pets' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getAllByRole('radio')).toHaveLength(7)
    expect(screen.getByRole('radio', { name: '🐾 Pets' })).toBeChecked()
  })

  it('remembers the account last used', async () => {
    const workspace = seedMockFamily()
    // Sorted after Carteira, so it is not what the form starts on.
    const poupanca = addAccount(workspace, 'Poupança', 'SAVINGS')
    const { router } = await openForm()

    const sheet = openContext()
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Poupança' }))
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await saveExpense(router)
    expect((await db.outbox.toArray())[0].payload.accountId).toBe(poupanca.id)

    await router.navigate('/lancar')
    expect(await screen.findByRole('button', { name: 'Hoje · Poupança · Eu' })).toBeInTheDocument()
  })

  it('takes the expense back with Desfazer', async () => {
    seedMockFamily()
    const { router } = await openForm()
    await saveExpense(router)

    fireEvent.click(await within(toastRegion()).findByRole('button', { name: 'Desfazer' }))

    expect(await within(toastRegion()).findByText('Lançamento desfeito.')).toBeInTheDocument()
    expect(await db.outbox.count()).toBe(0)
  })

  it('says something went wrong when Desfazer fails', async () => {
    seedMockFamily()
    const { router } = await openForm()
    await saveExpense(router)
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(db, 'transaction').mockRejectedValueOnce(new Error('broken'))

    fireEvent.click(await within(toastRegion()).findByRole('button', { name: 'Desfazer' }))

    expect(
      await within(toastRegion()).findByText('Não deu certo. Tente de novo.'),
    ).toBeInTheDocument()
    expect(consoleError).toHaveBeenCalled()
  })

  it('counts a transaction the server does not have as undone', async () => {
    seedMockFamily()
    const { router } = await openForm()
    await saveExpense(router)
    // The queue lost it and the API never stored it: the 404 means there is nothing left to undo.
    await db.outbox.clear()

    fireEvent.click(await within(toastRegion()).findByRole('button', { name: 'Desfazer' }))

    expect(await within(toastRegion()).findByText('Lançamento desfeito.')).toBeInTheDocument()
  })

  it('undoAfterSendDeletes', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    seedMockFamily()
    const { router, queryClient } = await openForm()
    await saveExpense(router)
    const [queued] = await db.outbox.toArray()
    await sendQueue()
    expect(await db.outbox.count()).toBe(0)
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    fireEvent.click(await within(toastRegion()).findByRole('button', { name: 'Desfazer' }))

    expect(await within(toastRegion()).findByText('Lançamento desfeito.')).toBeInTheDocument()
    expect(mockState.deletedTransactionIds.has(queued.id)).toBe(true)
    expect(invalidate).toHaveBeenCalled()
  })

  it('undoAfterSendOffline', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    seedMockFamily()
    const { router } = await openForm()
    await saveExpense(router)
    await sendQueue()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)

    fireEvent.click(await within(toastRegion()).findByRole('button', { name: 'Desfazer' }))

    expect(
      await within(toastRegion()).findByText(
        'Sem internet agora. Para desfazer, exclua em Lançamentos quando a conexão voltar.',
      ),
    ).toBeInTheDocument()
    expect(mockState.deletedTransactionIds.size).toBe(0)
  })

  it('says to delete it in Lançamentos when the undo is refused', async () => {
    seedMockFamily()
    server.use(
      http.delete(`${API_URL}/v1/workspaces/:ws/transactions/:id`, () =>
        problem(500, 'internal.error'),
      ),
    )
    const { router } = await openForm()
    await saveExpense(router)
    await db.outbox.clear()

    fireEvent.click(await within(toastRegion()).findByRole('button', { name: 'Desfazer' }))

    expect(
      await within(toastRegion()).findByText(
        'Não deu para desfazer. Tente excluir em Lançamentos.',
      ),
    ).toBeInTheDocument()
  })

  it('recordsIncome', async () => {
    const workspace = seedMockFamily()
    const { router } = await openForm()

    fireEvent.click(screen.getByRole('tab', { name: 'Receita' }))
    fireEvent.click(await screen.findByRole('radio', { name: '💼 Salário' }))
    press('5', '0', '0', '0', '0', '0')
    expect(amount()).toHaveTextContent('R$ 5.000,00')
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const items = await db.outbox.toArray()
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({
      workspaceId: workspace.id,
      payload: {
        kind: 'INCOME',
        amount: { amount: '5000.00', currency: 'BRL' },
        categoryId: category('Salário').id,
        accountId: mockState.accounts[0].id,
        toAccountId: null,
      },
    })
    expect(await within(toastRegion()).findByText('Receita salva.')).toBeInTheDocument()
    expect(within(toastRegion()).getByRole('button', { name: 'Desfazer' })).toBeInTheDocument()
  })

  it('recordsTransfer', async () => {
    const workspace = seedMockFamily()
    const carteira = mockState.accounts[0]
    const banco = addAccount(workspace, 'Banco')
    const { router } = await openForm()

    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    expect(screen.queryByRole('group', { name: 'Categoria' })).not.toBeInTheDocument()
    press('5', '0', '0', '0', '0')
    fireEvent.click(within(group('De')).getByRole('radio', { name: 'Carteira' }))
    expect(saveButton()).toBeDisabled()
    fireEvent.click(within(group('Para')).getByRole('radio', { name: 'Banco' }))
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const items = await db.outbox.toArray()
    expect(items).toHaveLength(1)
    expect(items[0].payload).toMatchObject({
      kind: 'TRANSFER',
      amount: { amount: '500.00', currency: 'BRL' },
      accountId: carteira.id,
      toAccountId: banco.id,
      categoryId: null,
    })
    expect(await within(toastRegion()).findByText('Transferência salva.')).toBeInTheDocument()
  })

  it('transferNeedsTwoAccounts', async () => {
    seedMockFamily()
    await openForm()

    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))

    expect(
      await screen.findByText('Para transferir, cadastre outra conta.', { exact: false }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cadastrar conta' })).toHaveAttribute(
      'href',
      '/mais/contas',
    )
    expect(screen.queryByRole('button', { name: 'Salvar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'De' })).not.toBeInTheDocument()
  })

  it('sameAccountNotOffered', async () => {
    const workspace = seedMockFamily()
    addAccount(workspace, 'Banco')
    addAccount(workspace, 'Poupança', 'SAVINGS')
    await openForm()

    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    fireEvent.click(within(group('De')).getByRole('radio', { name: 'Carteira' }))

    const to = within(group('Para'))
    expect(to.queryByRole('radio', { name: 'Carteira' })).not.toBeInTheDocument()
    expect(to.getAllByRole('radio').map((radio) => radio.parentElement?.textContent)).toEqual([
      'Banco',
      'Poupança',
    ])

    // Taking the Para account as De leaves Para to be chosen again.
    fireEvent.click(to.getByRole('radio', { name: 'Banco' }))
    fireEvent.click(within(group('De')).getByRole('radio', { name: 'Banco' }))
    expect(within(group('Para')).queryByRole('radio', { name: 'Banco' })).not.toBeInTheDocument()
    expect(within(group('Para')).getByRole('radio', { name: 'Carteira' })).not.toBeChecked()
  })

  it('opensTransferToCard', async () => {
    const workspace = seedMockFamily()
    const card = addAccount(workspace, 'Cartão Teste', 'CREDIT_CARD')
    renderPage(`/lancar?tipo=transferencia&para=${card.id}`)

    const to = await screen.findByRole('group', { name: 'Para' })
    expect(screen.getByRole('tab', { name: 'Transferência' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(within(to).getByRole('radio', { name: 'Cartão Teste' })).toBeChecked()
    // De starts on another account, never on the card itself.
    expect(within(group('De')).getByRole('radio', { name: 'Carteira' })).toBeChecked()
  })

  it('opens on Receita with ?tipo=receita', async () => {
    seedMockFamily()
    renderPage('/lancar?tipo=receita')

    expect(await screen.findByRole('radio', { name: '💼 Salário' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Receita' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Receita')
  })

  it('switchingTabKeepsAmount', async () => {
    seedMockFamily()
    await openForm()

    press('8', '7', '9', '0')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))
    const sheet = openContext()
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Ontem' }))
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    fireEvent.click(screen.getByRole('tab', { name: 'Receita' }))

    await screen.findByRole('radio', { name: '💼 Salário' })
    expect(amount()).toHaveTextContent('R$ 87,90')
    expect(screen.getByRole('button', { name: 'Ontem · Carteira · Eu' })).toBeInTheDocument()
    expect(
      screen.getAllByRole('radio').filter((radio) => (radio as HTMLInputElement).checked),
    ).toHaveLength(0)
    expect(saveButton()).toBeDisabled()

    fireEvent.click(screen.getByRole('tab', { name: 'Gasto' }))
    expect(await screen.findByRole('radio', { name: '🛒 Mercado' })).not.toBeChecked()
  })

  it('titleFollowsKind', async () => {
    seedMockFamily()
    await openForm()
    const heading = () => screen.getByRole('heading', { level: 1 })

    expect(heading()).toHaveTextContent('Novo gasto')
    expect(document.title).toContain('Novo gasto')
    fireEvent.click(screen.getByRole('tab', { name: 'Receita' }))
    expect(heading()).toHaveTextContent('Nova receita')
    expect(document.title).toContain('Nova receita')
    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    expect(heading()).toHaveTextContent('Nova transferência')
    expect(document.title).toContain('Nova transferência')
  })

  it('memberLineOnlyOnExpense', async () => {
    const workspace = seedMockFamily()
    addAccount(workspace, 'Banco')
    await openForm()

    fireEvent.click(screen.getByRole('tab', { name: 'Receita' }))
    await screen.findByRole('radio', { name: '💼 Salário' })
    const income = openContext()
    expect(within(income).queryByText('Quem gastou: Eu')).not.toBeInTheDocument()
    fireEvent.click(within(income).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    await screen.findByRole('group', { name: 'Para' })
    fireEvent.click(screen.getByRole('button', { name: /· Eu$/ }))
    const transfer = screen.getByRole('dialog', { name: 'Data' })
    expect(within(transfer).queryByText('Quem gastou: Eu')).not.toBeInTheDocument()
  })

  it('datePickerBounds', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    // Still 16 November in Fortaleza: the last day is worked out in the family time zone.
    vi.setSystemTime('2026-11-17T02:00:00Z')
    seedMockFamily()
    await openForm()

    const sheet = openContext()
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Outra data' }))
    const input = within(sheet).getByLabelText('Outra data', { selector: 'input[type="date"]' })

    expect(input).toHaveAttribute('min', '2000-01-01')
    expect(input).toHaveAttribute('max', '2027-11-17')
  })

  it('keeps Salvar disabled for a date out of range', async () => {
    seedMockFamily()
    await openForm()
    press('1', '0', '0')
    fireEvent.click(screen.getByRole('radio', { name: '🛒 Mercado' }))

    const sheet = openContext()
    fireEvent.click(within(sheet).getByRole('radio', { name: 'Outra data' }))
    fireEvent.change(
      within(sheet).getByLabelText('Outra data', { selector: 'input[type="date"]' }),
      { target: { value: '1999-12-31' } },
    )
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    expect(saveButton()).toBeDisabled()
  })

  it('archivedHidden', async () => {
    const workspace = seedMockFamily()
    category('Mercado').archivedAt = '2026-11-01T12:00:00Z'
    addAccount(workspace, 'Conta Antiga', 'CHECKING', '2026-11-01T12:00:00Z')
    renderPage()

    expect(await screen.findByRole('radio', { name: '🍽️ Alimentação fora' })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: '🛒 Mercado' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Mais' }))
    const all = screen.getByRole('dialog', { name: 'Todas as categorias' })
    expect(within(all).queryByRole('button', { name: '🛒 Mercado' })).not.toBeInTheDocument()
    fireEvent.click(within(all).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    const sheet = openContext()
    expect(within(sheet).getByRole('radio', { name: 'Carteira' })).toBeInTheDocument()
    expect(within(sheet).queryByRole('radio', { name: 'Conta Antiga' })).not.toBeInTheDocument()
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    // The archived account does not count either: the family has one account to move money from.
    fireEvent.click(screen.getByRole('tab', { name: 'Transferência' }))
    expect(
      screen.getByText('Para transferir, cadastre outra conta.', { exact: false }),
    ).toBeInTheDocument()
  })

  it('opens a refused expense filled in and replaces it with a new id', async () => {
    const workspace = seedMockFamily()
    await db.outbox.add(
      outboxItem('0192f3a4-0000-7000-8000-0000000000f1', {
        userId: mockState.user.id,
        workspaceId: workspace.id,
        status: 'needs-attention',
        attempts: 1,
        problem: { code: 'transaction.account-not-found', detail: 'Conta arquivada.' },
        payload: outboxPayload('0192f3a4-0000-7000-8000-0000000000f1', {
          accountId: mockState.accounts[0].id,
          categoryId: category('Mercado').id,
        }),
      }),
    )
    const { router } = await openForm('/lancar?corrigir=0192f3a4-0000-7000-8000-0000000000f1')

    await waitFor(() => expect(amount()).toHaveTextContent('R$ 12,34'))
    expect(screen.getByRole('radio', { name: '🛒 Mercado' })).toBeChecked()
    expect(screen.getByRole('button', { name: '3 de nov. · Carteira · Eu' })).toBeInTheDocument()
    press('Apagar', 'Apagar', 'Apagar', 'Apagar', '5', '0', '0')
    fireEvent.click(saveButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
    const items = await db.outbox.toArray()
    expect(items).toHaveLength(1)
    expect(items[0].id).not.toBe('0192f3a4-0000-7000-8000-0000000000f1')
    expect(items[0]).toMatchObject({
      status: 'pending',
      attempts: 0,
      createdAt: 1000,
      payload: {
        id: items[0].id,
        amount: { amount: '5.00', currency: 'BRL' },
        occurredOn: '2026-11-03',
      },
    })
    // Desfazer would delete the fix and, with it, the refused expense (Ruling T11-correction-undo).
    expect(await within(toastRegion()).findByText('Gasto salvo.')).toBeInTheDocument()
    expect(
      within(toastRegion()).queryByRole('button', { name: 'Desfazer' }),
    ).not.toBeInTheDocument()
  })

  it('keeps Salvar disabled when the category to fix is not offered', async () => {
    const workspace = seedMockFamily()
    await db.outbox.add(
      outboxItem('0192f3a4-0000-7000-8000-0000000000f1', {
        userId: mockState.user.id,
        workspaceId: workspace.id,
        status: 'needs-attention',
        attempts: 1,
        payload: outboxPayload('0192f3a4-0000-7000-8000-0000000000f1', {
          accountId: mockState.accounts[0].id,
          categoryId: '0192f3a4-0000-7000-8000-0000000000c9',
        }),
      }),
    )
    await openForm('/lancar?corrigir=0192f3a4-0000-7000-8000-0000000000f1')

    await waitFor(() => expect(amount()).toHaveTextContent('R$ 12,34'))
    expect(saveButton()).toBeDisabled()
  })

  it('opens empty when the item to fix is not in the queue', async () => {
    seedMockFamily()
    await openForm('/lancar?corrigir=0192f3a4-0000-7000-8000-0000000000f1')

    expect(amount()).toHaveTextContent('R$ 0,00')
    expect(screen.getByRole('radio', { name: '🛒 Mercado' })).not.toBeChecked()
  })

  it('asks to open the app online once when the categories never loaded', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    server.use(http.get(`${API_URL}/v1/workspaces/:ws/categories`, () => HttpResponse.error()))
    renderPage()

    expect(
      await screen.findByText('Abra o app com internet uma vez para carregar suas categorias.'),
    ).toBeInTheDocument()
    press('5')
    expect(saveButton()).toBeDisabled()
  })

  it('offers to try again when the categories fail online', async () => {
    seedMockFamily()
    let failed = false
    server.use(
      // The form loads both kinds; only the expense list, the open tab's, fails, and only once.
      http.get(`${API_URL}/v1/workspaces/:ws/categories`, ({ request }) => {
        if (failed || new URL(request.url).searchParams.get('kind') !== 'EXPENSE') return undefined
        failed = true
        return problem(500, 'internal.error')
      }),
    )
    renderPage()

    expect(await screen.findByText('Não deu para carregar. Tente de novo.')).toBeInTheDocument()
    expect(
      screen.queryByText('Abra o app com internet uma vez para carregar suas categorias.'),
    ).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))

    expect(await screen.findByRole('radio', { name: '🛒 Mercado' })).toBeInTheDocument()
  })

  it('ignores the keyboard while a sheet is open or with Ctrl, Alt or Meta', async () => {
    seedMockFamily()
    await openForm()

    fireEvent.click(screen.getByRole('button', { name: 'Mais' }))
    const sheet = screen.getByRole('dialog', { name: 'Todas as categorias' })
    fireEvent.keyDown(document.body, { key: '5' })
    expect(amount()).toHaveTextContent('R$ 0,00')
    fireEvent.click(within(sheet).getByRole('button', { name: 'Fechar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    for (const modifier of ['ctrlKey', 'altKey', 'metaKey']) {
      fireEvent.keyDown(document.body, { key: '5', [modifier]: true })
    }
    expect(amount()).toHaveTextContent('R$ 0,00')
    fireEvent.keyDown(document.body, { key: '5' })
    expect(amount()).toHaveTextContent('R$ 0,05')
  })
})

describe('ExpenseDate', () => {
  it('is a preset or a YYYY-MM-DD date', () => {
    expectTypeOf<'2026-11-03'>().toExtend<ExpenseDate>()
    expectTypeOf<'amanhã'>().not.toExtend<ExpenseDate>()
  })
})
