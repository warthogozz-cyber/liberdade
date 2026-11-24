import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import type { AccountView, MemberView } from '@/shared/api'
import { outboxPayload, renderWithProviders, transactionView } from '@/shared/testing'
import { markRemoteArrival, resetLocalChanges } from '@/shared/live/localChanges'
import { TransactionRow } from './TransactionRow'

afterEach(() => {
  resetLocalChanges()
  cleanup()
  vi.restoreAllMocks()
})

const removed = (overrides: Partial<MemberView>): MemberView => ({
  id: 'm-tio',
  displayName: null,
  nickname: null,
  role: 'ADULT',
  status: 'REMOVED',
  you: false,
  passkeyOnly: false,
  joinedAt: '2026-11-01T12:00:00Z',
  ...overrides,
})

function renderRow(member: MemberView) {
  const workspace = seedMockFamily()
  return renderWithProviders(
    <ul>
      <TransactionRow
        item={{ ...transactionView(outboxPayload('t1')), memberId: member.id, createdAtMs: 0 }}
        categories={new Map()}
        accounts={new Map()}
        workspace={workspace}
        displayName="Ana Teste"
        members={new Map([[member.id, member]])}
        month="2026-11"
        online
        onDelete={() => {}}
      />
    </ul>,
  )
}

describe('TransactionRow', () => {
  it('arrivalGetsHighlight', () => {
    markRemoteArrival('t1')
    renderRow(removed({ nickname: 'Tio' }))
    expect(screen.getByRole('listitem')).toHaveClass('motion-safe:animate-arrive')
  })

  it('anOrdinaryRowGetsNoHighlight', () => {
    renderRow(removed({ nickname: 'Tio' }))
    expect(screen.getByRole('listitem')).not.toHaveClass('motion-safe:animate-arrive')
  })

  it('removedMemberName', () => {
    renderRow(removed({ nickname: 'Tio' }))
    expect(screen.getByText('Tio (saiu)')).toBeInTheDocument()
  })

  it('removedMemberWithoutAName', () => {
    renderRow(removed({}))
    expect(screen.getByText('Ex-membro')).toBeInTheDocument()
    expect(screen.queryByText(/saiu/)).not.toBeInTheDocument()
  })

  it('detailNamesTheFamilyAccountForAYouth', () => {
    const workspace = seedMockFamily()
    workspace.role = 'YOUTH'
    const wallet = { ...mockState.accounts[0], name: 'Carteira de Leo' } as unknown as AccountView
    renderWithProviders(
      <ul>
        <TransactionRow
          item={{
            ...transactionView(
              outboxPayload('t1', {
                kind: 'TRANSFER',
                categoryId: null,
                // The allowance leaves an account a Youth cannot see.
                accountId: '0192f3a4-0000-7000-8000-0000000000a9',
                toAccountId: wallet.id,
              }),
            ),
            createdAtMs: 0,
          }}
          categories={new Map()}
          accounts={new Map([[wallet.id, wallet]])}
          workspace={workspace}
          displayName="Leo Teste"
          month="2026-11"
          online
          onDelete={() => {}}
        />
      </ul>,
    )

    fireEvent.click(screen.getByRole('button'))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Conta da família')).toBeInTheDocument()
    expect(within(dialog).getByText('Carteira de Leo')).toBeInTheDocument()
  })
})
