import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { RecoveryNotice } from './RecoveryNotice'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function renderNotice(recoveredAt: string) {
  // The same membership each time, as the same person coming back to Home.
  const workspace = { ...seedMockFamily(), membershipId: 'membership-ana', recoveredAt }
  return renderWithProviders(<RecoveryNotice workspace={workspace} />)
}

describe('RecoveryNotice', () => {
  it('staysDismissedForTheSameRecovery', () => {
    renderNotice('2026-11-13T15:00:00Z')
    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    cleanup()
    renderNotice('2026-11-13T15:00:00Z')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('showsAgainForANewerRecovery', () => {
    renderNotice('2026-11-13T15:00:00Z')
    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }))

    cleanup()
    renderNotice('2026-11-15T09:00:00Z')
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})
