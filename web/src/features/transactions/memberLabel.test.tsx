import { cleanup, screen } from '@testing-library/react'
import { useIntl } from 'react-intl'
import { afterEach, describe, expect, it } from 'vitest'
import type { MemberView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { memberLabel } from './memberLabel'

afterEach(cleanup)

const member = (overrides: Partial<MemberView>): MemberView => ({
  id: 'm-1',
  displayName: null,
  nickname: null,
  role: 'ADULT',
  status: 'ACTIVE',
  you: false,
  passkeyOnly: false,
  joinedAt: '2026-11-01T12:00:00Z',
  ...overrides,
})

function Label({ of }: { of: MemberView | undefined }) {
  return <p>{memberLabel(of, useIntl()) ?? 'nobody'}</p>
}

describe('memberLabel', () => {
  it.each([
    ['not loaded yet', undefined, 'nobody'],
    ['active', member({ nickname: 'Ana' }), 'Ana'],
    ['removed with a name', member({ nickname: 'Tio', status: 'REMOVED' }), 'Tio (saiu)'],
    ['removed without a name', member({ status: 'REMOVED' }), 'Ex-membro'],
  ])('says %s', (_, of, text) => {
    renderWithProviders(<Label of={of} />)
    expect(screen.getByText(text)).toBeInTheDocument()
  })
})
