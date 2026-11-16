import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL, type AccountView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
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

describe('OwnerPicker', () => {
  it('setsAndClearsOwner', async () => {
    const { workspace, account, puts } = setUp()
    renderWithProviders(
      <OwnerPicker workspace={workspace} account={account as unknown as AccountView} />,
    )

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))
    await waitFor(() => expect(puts).toEqual([{ membershipId: LEO_ID }]))
    expect(mockState.accounts[0].ownerMembershipId).toBe(LEO_ID)

    fireEvent.click(screen.getByRole('radio', { name: 'Ninguém' }))
    await waitFor(() => expect(puts).toEqual([{ membershipId: LEO_ID }, { membershipId: null }]))
    expect(mockState.accounts[0].ownerMembershipId).toBeNull()
  })

  it('putsTheOwnerBackWhenSavingFails', async () => {
    const { workspace, account } = setUp()
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.put(PUT_URL, () => HttpResponse.json({ code: 'x' }, { status: 500 })))
    renderWithProviders(
      <OwnerPicker workspace={workspace} account={account as unknown as AccountView} />,
    )

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))

    await waitFor(() => expect(screen.getByRole('radio', { name: 'Leo' })).not.toBeChecked())
    expect(screen.getByRole('radio', { name: 'Ninguém' })).toBeChecked()
  })

  it('onlyReportsWithoutAnAccount', async () => {
    const { workspace } = setUp()
    const onChange = vi.fn()
    renderWithProviders(<OwnerPicker workspace={workspace} value={null} onChange={onChange} />)

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))

    expect(onChange).toHaveBeenCalledWith(LEO_ID)
  })
})
