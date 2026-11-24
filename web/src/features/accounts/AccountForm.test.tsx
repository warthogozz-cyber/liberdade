import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type AccountView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { AccountForm } from './AccountForm'

afterEach(cleanup)

function renderForm() {
  const workspace = seedMockFamily()
  return renderWithProviders(<AccountForm workspace={workspace} onDone={() => {}} />)
}

describe('AccountForm', () => {
  it('showsTypeHint', () => {
    renderForm()
    expect(screen.getByText('Para o dia a dia: salário, contas, Pix.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('radio', { name: /Cartão de crédito/ }))

    expect(
      screen.getByText(
        'Os gastos deixam o saldo negativo. Pagar a fatura é uma transferência de outra conta para o cartão.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByText('Para o dia a dia: salário, contas, Pix.')).not.toBeInTheDocument()
  })

  it('names the type group and describes it with the hint', () => {
    renderForm()

    fireEvent.click(screen.getByRole('radio', { name: /Cartão de crédito/ }))

    expect(screen.getByRole('radiogroup', { name: 'Tipo' })).toHaveAccessibleDescription(
      'Os gastos deixam o saldo negativo. Pagar a fatura é uma transferência de outra conta para o cartão.',
    )
  })

  it('does not save without a name', () => {
    renderForm()

    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled()
  })
})

describe('AccountForm owner', () => {
  it('createsWithTheChosenOwner', async () => {
    const LEO_ID = '0192f3a4-0000-7000-8000-0000000000a2'
    const workspace = seedMockFamily()
    mockState.members[workspace.id]!.push({
      id: LEO_ID,
      displayName: 'Leo Teste',
      nickname: 'Leo',
      role: 'YOUTH',
      status: 'ACTIVE',
      you: false,
      passkeyOnly: false,
      joinedAt: '2026-11-01T12:00:00Z',
    })
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts`, async ({ request }) => {
        bodies.push(await request.clone().json())
        return undefined
      }),
    )
    renderWithProviders(<AccountForm workspace={workspace} onDone={() => {}} />)

    fireEvent.change(screen.getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Mesada' },
    })
    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual(
      expect.objectContaining({ name: 'Mesada', ownerMembershipId: LEO_ID }),
    )
  })

  it('leavesOutTheOwnerWhenNobodyIsChosen', async () => {
    const workspace = seedMockFamily()
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts`, async ({ request }) => {
        bodies.push(await request.clone().json())
        return undefined
      }),
    )
    renderWithProviders(<AccountForm workspace={workspace} onDone={() => {}} />)

    fireEvent.change(screen.getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Banco' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).not.toHaveProperty('ownerMembershipId')
  })

  it('hidesTheOwnerFromAYouth', () => {
    const workspace = seedMockFamily()
    workspace.role = 'YOUTH'
    renderWithProviders(<AccountForm workspace={workspace} onDone={() => {}} />)

    expect(screen.queryByRole('group', { name: 'Titular' })).not.toBeInTheDocument()
  })
})

describe('AccountForm reserve toggle', () => {
  const edited = () => {
    const workspace = seedMockFamily()
    const stored = mockState.accounts[0]
    const account: AccountView = {
      ...stored,
      balance: stored.initialBalance,
    }
    return { workspace, account }
  }

  function capture() {
    const bodies: Record<string, unknown>[] = []
    server.use(
      http.patch(`${API_URL}/v1/workspaces/:ws/accounts/:id`, async ({ request }) => {
        bodies.push((await request.clone().json()) as Record<string, unknown>)
        return undefined
      }),
    )
    return bodies
  }

  it('showsTheToggleWithTheModuleOnForAnAdult', async () => {
    const { workspace, account } = edited()
    workspace.role = 'ADULT'
    mockState.modules[workspace.id] = { planning: true }
    renderWithProviders(<AccountForm workspace={workspace} account={account} onDone={() => {}} />)

    expect(await screen.findByRole('checkbox', { name: 'Esta conta é reserva' })).not.toBeChecked()
  })

  it('hidesTheToggleWithTheModuleOff', async () => {
    const { workspace, account } = edited()
    renderWithProviders(<AccountForm workspace={workspace} account={account} onDone={() => {}} />)

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('checkbox', { name: 'Esta conta é reserva' })).not.toBeInTheDocument()
  })

  it('alwaysSendsTheFlagWhenShown', async () => {
    const { workspace, account } = edited()
    mockState.modules[workspace.id] = { planning: true }
    const bodies = capture()
    renderWithProviders(<AccountForm workspace={workspace} account={account} onDone={() => {}} />)

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Esta conta é reserva' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Outro nome' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({ name: 'Outro nome', isReserve: true })
  })

  it('sendsNoFlagWhenTheToggleIsHidden', async () => {
    const { workspace, account } = edited()
    const bodies = capture()
    renderWithProviders(<AccountForm workspace={workspace} account={account} onDone={() => {}} />)

    fireEvent.change(screen.getByRole('textbox', { name: 'Nome' }), {
      target: { value: 'Outro nome' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({ name: 'Outro nome' })
  })
})
