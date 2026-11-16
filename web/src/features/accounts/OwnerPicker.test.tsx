import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type AccountView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { AccountForm } from './AccountForm'
import { OwnerPicker } from './OwnerPicker'

const LEO_ID = '0192f3a4-0000-7000-8000-0000000000a2'
const PUT_URL = `${API_URL}/v1/workspaces/:ws/accounts/:id/owner`

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function setUp() {
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
  const account = mockState.accounts[0]
  account.ownerMembershipId = null
  const puts: unknown[] = []
  server.use(
    http.put(PUT_URL, async ({ request }) => {
      puts.push(await request.clone().json())
      return undefined
    }),
  )
  return { workspace, account, puts }
}

function renderForm(workspace: ReturnType<typeof seedMockFamily>, onDone = () => {}) {
  return renderWithProviders(
    <AccountForm
      workspace={workspace}
      account={mockState.accounts[0] as unknown as AccountView}
      onDone={onDone}
    />,
  )
}

describe('OwnerPicker', () => {
  it('setsAndClearsOwner', async () => {
    const { workspace, puts } = setUp()
    renderForm(workspace)

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))
    // The choice waits for Salvar, together with the form.
    expect(puts).toEqual([])
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(puts).toEqual([{ membershipId: LEO_ID }]))
    expect(mockState.accounts[0].ownerMembershipId).toBe(LEO_ID)

    cleanup()
    renderForm(workspace)
    fireEvent.click(await screen.findByRole('radio', { name: 'Ninguém' }))
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(puts).toEqual([{ membershipId: LEO_ID }, { membershipId: null }]))
    expect(mockState.accounts[0].ownerMembershipId).toBeNull()
  })

  it('sendsNothingWithoutSalvar', async () => {
    const { workspace, puts } = setUp()
    renderForm(workspace)

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))
    cleanup()

    expect(puts).toEqual([])
    expect(mockState.accounts[0].ownerMembershipId).toBeNull()
  })

  it('saysSoWhenSavingTheOwnerFails', async () => {
    const { workspace } = setUp()
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.put(PUT_URL, () => HttpResponse.json({ code: 'x' }, { status: 500 })))
    const onDone = vi.fn()
    renderForm(workspace, onDone)

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(onDone).not.toHaveBeenCalled()
  })

  it('isDisabledOffline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const { workspace, puts } = setUp()
    renderForm(workspace)

    const leo = await screen.findByRole('radio', { name: 'Leo' })
    expect(leo).toBeDisabled()
    expect(screen.getByRole('radio', { name: 'Ninguém' })).toBeDisabled()
    fireEvent.click(leo)
    expect(puts).toEqual([])
  })

  it('showsAnOwnerWhoLeft', async () => {
    const { workspace } = setUp()
    mockState.members[workspace.id]!.push({
      id: 'm-old',
      displayName: 'Bia Teste',
      nickname: 'Bia',
      role: 'ADULT',
      status: 'REMOVED',
      you: false,
      passkeyOnly: false,
      joinedAt: '2026-10-01T12:00:00Z',
    })
    mockState.accounts[0].ownerMembershipId = 'm-old'
    renderWithProviders(<OwnerPicker workspace={workspace} value="m-old" onChange={() => {}} />)

    const left = await screen.findByRole('radio', { name: 'Bia (saiu)' })
    expect(left).toBeChecked()
    expect(left).toBeDisabled()
    expect(screen.getByRole('radio', { name: 'Ninguém' })).not.toBeChecked()
  })

  it('onlyReportsTheChoice', async () => {
    const { workspace } = setUp()
    const onChange = vi.fn()
    renderWithProviders(<OwnerPicker workspace={workspace} value={null} onChange={onChange} />)

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))

    expect(onChange).toHaveBeenCalledWith(LEO_ID)
  })
})
