import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily, type MockAccount } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { AccountsPage } from './AccountsPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function addAccount(overrides: Partial<MockAccount> & { name: string }): MockAccount {
  const account: MockAccount = {
    id: crypto.randomUUID(),
    type: 'CHECKING',
    currency: 'BRL',
    initialBalance: { amount: '0.00', currency: 'BRL' },
    ownerMembershipId: null,
    archivedAt: null,
    ...overrides,
  }
  mockState.accounts.push(account)
  return account
}

function balance(amount: string) {
  return { amount, currency: 'BRL' }
}

function renderAccounts() {
  return renderWithProviders(<AccountsPage />, { path: '/mais/contas' })
}

const row = (name: string) => screen.findByRole('button', { name: new RegExp(name) })
const toasts = () => within(screen.getByRole('region', { name: 'Notificações', hidden: true }))

function press(keys: string[]) {
  for (const key of keys) fireEvent.click(screen.getByRole('button', { name: key }))
}

/** Records the JSON body of the next PATCH to an account, letting the mock API answer it. */
function capturePatch() {
  const captured: { body?: unknown } = {}
  server.use(
    http.patch(`${API_URL}/v1/workspaces/:ws/accounts/:id`, async ({ request }) => {
      captured.body = await request.clone().json()
    }),
  )
  return captured
}

describe('AccountsPage', () => {
  it('listsActiveAndArchived', async () => {
    seedMockFamily()
    addAccount({ name: 'Banco Teste', initialBalance: balance('1500.50') })
    addAccount({ name: 'Cartão Teste', type: 'CREDIT_CARD', initialBalance: balance('-300.00') })
    addAccount({ name: 'Conta Antiga', archivedAt: '2026-10-01T10:00:00Z' })
    renderAccounts()

    const bank = await row('Banco Teste')
    expect(within(bank).getByText('Conta corrente')).toBeInTheDocument()
    expect(within(bank).getByText('R$ 1.500,50')).toBeInTheDocument()
    expect(within(await row('Cartão Teste')).getByText('−R$ 300,00')).toBeInTheDocument()
    expect(within(await row('Carteira')).getByText('R$ 0,00')).toBeInTheDocument()
    const archived = screen.getByText('Arquivadas (1)').closest('details')!
    expect(archived).not.toHaveAttribute('open')
    expect(within(archived).getByText(/Conta Antiga/)).toBeInTheDocument()
    expect(
      within(archived).getByRole('button', { name: 'Reativar', hidden: true }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Conta Antiga/ })).not.toBeInTheDocument()
    expect(document.title).toBe('Contas · Liberdade')
  })

  it('createsAccount', async () => {
    seedMockFamily()
    renderAccounts()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova conta' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova conta' })
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Corrente' },
    })
    fireEvent.click(within(sheet).getByRole('radio', { name: /Conta corrente/ }))
    press(['1', '0', '0', '0', '00'])
    expect(within(sheet).getByText('R$ 1.000,00')).toBeInTheDocument()
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await toasts().findByText('Conta criada.')).toBeInTheDocument()
    const created = mockState.accounts.find((account) => account.name === 'Corrente')!
    expect(created.type).toBe('CHECKING')
    expect(created.currency).toBe('BRL')
    expect(created.initialBalance).toEqual(balance('1000.00'))
    expect(await row('Corrente')).toBeInTheDocument()
  })

  it('createsNegativeBalance', async () => {
    seedMockFamily()
    renderAccounts()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova conta' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova conta' })
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Cartão Teste' },
    })
    fireEvent.click(within(sheet).getByRole('radio', { name: /Cartão de crédito/ }))
    press(['2', '5', '0', '00'])
    fireEvent.click(within(sheet).getByRole('switch', { name: 'Saldo negativo' }))
    expect(within(sheet).getByText('−R$ 250,00')).toBeInTheDocument()
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    await toasts().findByText('Conta criada.')
    const created = mockState.accounts.find((account) => account.name === 'Cartão Teste')!
    expect(created.initialBalance).toEqual(balance('-250.00'))
  })

  it('nameTakenOnField', async () => {
    seedMockFamily()
    renderAccounts()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova conta' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova conta' })
    const name = within(sheet).getByRole('textbox', { name: 'Nome' })
    fireEvent.change(name, { target: { value: 'Carteira' } })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    await waitFor(() =>
      expect(name).toHaveAccessibleDescription('Já existe uma conta com esse nome.'),
    )
    expect(name).toHaveAttribute('aria-invalid', 'true')
    expect(mockState.accounts).toHaveLength(1)
  })

  it('shows other save errors as text in the sheet', async () => {
    seedMockFamily()
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts`, () =>
        HttpResponse.json(
          { status: 422, code: 'account.currency-unsupported', detail: 'Moeda não aceita.' },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderAccounts()

    fireEvent.click(await screen.findByRole('button', { name: 'Nova conta' }))
    const sheet = await screen.findByRole('dialog', { name: 'Nova conta' })
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Outra' },
    })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await within(sheet).findByRole('alert')).toHaveTextContent('Moeda não aceita.')
  })

  it('savesOnlyWhatChanged', async () => {
    seedMockFamily()
    renderAccounts()
    const captured = capturePatch()

    fireEvent.click(await row('Carteira'))
    const sheet = await screen.findByRole('dialog', { name: 'Carteira' })
    expect(within(sheet).getByRole('button', { name: 'Salvar' })).toBeDisabled()
    fireEvent.change(within(sheet).getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Bolso' },
    })
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await toasts().findByText('Conta salva.')).toBeInTheDocument()
    expect(captured.body).toEqual({ name: 'Bolso' })
    expect(mockState.accounts[0].name).toBe('Bolso')
  })

  it('adjustsBalance', async () => {
    seedMockFamily()
    renderAccounts()
    const captured = capturePatch()

    fireEvent.click(await row('Carteira'))
    fireEvent.click(await screen.findByRole('button', { name: 'Acertar saldo' }))
    const sheet = await screen.findByRole('dialog', { name: 'Acertar saldo' })
    expect(within(sheet).getByText('Qual é o saldo hoje?')).toBeInTheDocument()
    press(['4', '2', '6', '5', '4', '4'])
    fireEvent.click(within(sheet).getByRole('button', { name: 'Salvar' }))

    expect(await toasts().findByText('Saldo atualizado.')).toBeInTheDocument()
    expect(captured.body).toEqual({ balance: { amount: '4265.44', currency: 'BRL' } })
    expect(await within(await row('Carteira')).findByText('R$ 4.265,44')).toBeInTheDocument()
  })

  it('investmentLeadsWithAdjust', async () => {
    seedMockFamily()
    addAccount({ name: 'Corretora Teste', type: 'INVESTMENT' })
    renderAccounts()

    fireEvent.click(await row('Corretora Teste'))
    const sheet = await screen.findByRole('dialog', { name: 'Corretora Teste' })

    const buttons = within(sheet)
      .getAllByRole('button')
      .filter((button) => button.getAttribute('aria-label') !== 'Fechar')
    expect(buttons[0]).toHaveAccessibleName('Acertar saldo')
  })

  it('archiveWarnsAboutBalance', async () => {
    seedMockFamily()
    addAccount({ name: 'Banco Teste', initialBalance: balance('200.00') })
    renderAccounts()

    fireEvent.click(await row('Banco Teste'))
    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))

    const confirm = await screen.findByRole('alertdialog', { name: 'Arquivar Banco Teste?' })
    expect(confirm).toHaveTextContent('O saldo de R$ 200,00 sai do total da família.')
  })

  it('archives an account without balance and says so', async () => {
    seedMockFamily()
    addAccount({ name: 'Banco Teste' })
    renderAccounts()

    fireEvent.click(await row('Banco Teste'))
    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))
    const confirm = await screen.findByRole('alertdialog', { name: 'Arquivar Banco Teste?' })
    expect(confirm).toHaveTextContent('Ela some das escolhas.')
    fireEvent.click(within(confirm).getByRole('button', { name: 'Arquivar' }))

    expect(await toasts().findByText('Conta arquivada.')).toBeInTheDocument()
    expect(await screen.findByText('Arquivadas (1)')).toBeInTheDocument()
    const stored = mockState.accounts.find((account) => account.name === 'Banco Teste')
    expect(stored!.archivedAt).not.toBeNull()
  })

  it('archive question cites a negative balance with its sign', async () => {
    seedMockFamily()
    addAccount({ name: 'Cartão Teste', initialBalance: balance('-50.00') })
    renderAccounts()

    fireEvent.click(await row('Cartão Teste'))
    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))

    const confirm = await screen.findByRole('alertdialog', { name: 'Arquivar Cartão Teste?' })
    expect(confirm).toHaveTextContent('−R$ 50,00')
  })

  it('sends one archive call for a double click', async () => {
    seedMockFamily()
    addAccount({ name: 'Banco Teste' })
    let calls = 0
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts/:id/archive`, async () => {
        calls += 1
        await new Promise((resolve) => setTimeout(resolve, 50))
      }),
    )
    renderAccounts()

    fireEvent.click(await row('Banco Teste'))
    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))
    const confirm = await screen.findByRole('alertdialog')
    const button = within(confirm).getByRole('button', { name: 'Arquivar' })
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => expect(calls).toBeGreaterThan(0))
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(calls).toBe(1)
  })

  it('does not keep an archive error after Cancelar', async () => {
    seedMockFamily()
    renderAccounts()

    fireEvent.click(await row('Carteira'))
    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Arquivar' }),
    )
    const sheet = await screen.findByRole('dialog', { name: 'Carteira' })
    expect(await within(sheet).findByRole('alert')).toBeInTheDocument()

    fireEvent.click(within(sheet).getByRole('button', { name: 'Arquivar' }))
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }),
    )

    const again = await screen.findByRole('dialog', { name: 'Carteira' })
    expect(within(again).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('lastActiveExplains', async () => {
    seedMockFamily()
    renderAccounts()

    fireEvent.click(await row('Carteira'))
    fireEvent.click(await screen.findByRole('button', { name: 'Arquivar' }))
    const confirm = await screen.findByRole('alertdialog')
    fireEvent.click(within(confirm).getByRole('button', { name: 'Arquivar' }))

    const sheet = await screen.findByRole('dialog', { name: 'Carteira' })
    expect(await within(sheet).findByRole('alert')).toHaveTextContent(
      'A família precisa de pelo menos uma conta ativa.',
    )
  })

  it('unarchives', async () => {
    seedMockFamily()
    addAccount({ name: 'Conta Antiga', archivedAt: '2026-10-01T10:00:00Z' })
    renderAccounts()

    fireEvent.click(await screen.findByRole('button', { name: 'Reativar', hidden: true }))

    expect(await toasts().findByText('Conta reativada.')).toBeInTheDocument()
    const stored = mockState.accounts.find((account) => account.name === 'Conta Antiga')
    expect(stored!.archivedAt).toBeNull()
    expect(await row('Conta Antiga')).toBeInTheDocument()
  })

  it('payBillOpensTransfer', async () => {
    seedMockFamily()
    const card = addAccount({ name: 'Cartão Teste', type: 'CREDIT_CARD' })
    const { router } = renderAccounts()

    fireEvent.click(await row('Cartão Teste'))
    fireEvent.click(await screen.findByRole('link', { name: 'Pagar fatura' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancar'))
    expect(router.state.location.search).toBe(`?tipo=transferencia&para=${card.id}`)
  })

  it('offers no bill to pay on other accounts', async () => {
    seedMockFamily()
    renderAccounts()

    fireEvent.click(await row('Carteira'))
    await screen.findByRole('dialog', { name: 'Carteira' })

    expect(screen.queryByRole('link', { name: 'Pagar fatura' })).not.toBeInTheDocument()
  })

  it('offlineDisablesWrites', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    seedMockFamily()
    addAccount({ name: 'Conta Antiga', archivedAt: '2026-10-01T10:00:00Z' })
    renderAccounts()

    expect(await screen.findByRole('button', { name: 'Nova conta' })).toBeDisabled()
    expect(screen.getByText('Sem conexão. Mudar contas volta com o sinal.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Reativar', hidden: true })).toBeDisabled()
    fireEvent.click(await row('Carteira'))
    const sheet = await screen.findByRole('dialog', { name: 'Carteira' })
    expect(within(sheet).getByRole('button', { name: 'Acertar saldo' })).toBeDisabled()
    expect(within(sheet).getByRole('button', { name: 'Arquivar' })).toBeDisabled()
    expect(within(sheet).getByRole('button', { name: 'Salvar' })).toBeDisabled()
  })
})
