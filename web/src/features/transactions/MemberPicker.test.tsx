import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import type { MemberView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import MemberPicker from './MemberPicker'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function addMember(workspaceId: string, overrides: Partial<MemberView> & { id: string }) {
  mockState.members[workspaceId]!.push({
    displayName: 'Bia Teste',
    nickname: null,
    role: 'ADULT',
    status: 'ACTIVE',
    you: false,
    passkeyOnly: false,
    joinedAt: '2026-11-01T12:00:00Z',
    ...overrides,
  })
}

function renderPicker(onChange: (id: string) => void = () => {}) {
  const workspace = seedMockFamily()
  return {
    workspace,
    render: (value = workspace.membershipId) =>
      renderWithProviders(
        <MemberPicker
          workspaceId={workspace.id}
          selfId={workspace.membershipId}
          value={value}
          onChange={onChange}
        />,
      ),
  }
}

describe('MemberPicker', () => {
  it('defaultsToYou', async () => {
    const onChange = vi.fn()
    const { workspace, render } = renderPicker(onChange)
    addMember(workspace.id, { id: 'm-leo', nickname: 'Leo' })
    render()

    expect(await screen.findByRole('radio', { name: 'Leo' })).not.toBeChecked()
    expect(screen.getByRole('radio', { name: 'Você' })).toBeChecked()
    // Nothing was chosen, so nothing was reported.
    expect(onChange).not.toHaveBeenCalled()
  })

  it('listsOnlyActive', async () => {
    const { workspace, render } = renderPicker()
    addMember(workspace.id, { id: 'm-leo', nickname: 'Leo' })
    addMember(workspace.id, { id: 'm-tio', nickname: 'Tio', status: 'REMOVED' })
    render()

    await screen.findByRole('radio', { name: 'Leo' })
    expect(screen.queryByRole('radio', { name: 'Tio' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('radio').map((each) => each.closest('label')!.textContent)).toEqual([
      'Você',
      'Leo',
    ])
  })

  it('reportsTheChosenMember', async () => {
    const onChange = vi.fn()
    const { workspace, render } = renderPicker(onChange)
    addMember(workspace.id, { id: 'm-leo', nickname: 'Leo' })
    render()

    fireEvent.click(await screen.findByRole('radio', { name: 'Leo' }))

    expect(onChange).toHaveBeenCalledWith('m-leo', 'Leo')
  })

  it('showsOnlyYouDisabledWithoutTheList', () => {
    const { render } = renderPicker()
    render()

    const you = screen.getByRole('radio', { name: 'Você' })
    expect(you).toBeChecked()
    expect(you).toBeDisabled()
  })
})
