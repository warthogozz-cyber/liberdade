import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import type { MemberView } from '@/shared/api'
import { outboxPayload, renderWithProviders, transactionView } from '@/shared/testing'
import { TransactionRow } from './TransactionRow'

afterEach(() => {
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
  it('removedMemberName', () => {
    renderRow(removed({ nickname: 'Tio' }))
    expect(screen.getByText('Tio (saiu)')).toBeInTheDocument()
  })

  it('removedMemberWithoutAName', () => {
    renderRow(removed({}))
    expect(screen.getByText('Ex-membro')).toBeInTheDocument()
    expect(screen.queryByText(/saiu/)).not.toBeInTheDocument()
  })
})
