import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { PolicyUpdatedNotice } from './PolicyUpdatedNotice'

const KEY = 'liberdade.policyNotice.privacy-2026-12.4'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
})

/**
 * The policy in force is the .4 text, a version that keeps the acceptance of the ones it continues (the .5 text
 * asks for a new acceptance). The person accepted the policy before the .1 text, which the API says still counts.
 */
function acceptOldPolicy() {
  seedMockFamily()
  mockState.currentDocuments.PRIVACY = 'privacy-2026-12.4'
  mockState.acceptedDocuments.PRIVACY = { version: 'privacy-2026-12', at: '2026-11-20T10:00:00Z' }
}

const notice = () => screen.findByRole('status')

describe('PolicyUpdatedNotice', () => {
  it('showsTheNoticeWhenTheAcceptedVersionIsTheOldOne', async () => {
    acceptOldPolicy()
    renderWithProviders(<PolicyUpdatedNotice />)

    expect(await notice()).toHaveTextContent('Atualizamos a política de privacidade.')
    const read = screen.getByRole('link', { name: /Ler/ })
    expect(read).toHaveAttribute('href', '/privacidade')
    expect(screen.getByRole('button', { name: 'Fechar' })).toBeVisible()
  })

  it('showsTheNoticeToWhoAcceptedTheTwelveDotTwoText', async () => {
    seedMockFamily()
    mockState.currentDocuments.PRIVACY = 'privacy-2026-12.4'
    mockState.acceptedDocuments.PRIVACY = {
      version: 'privacy-2026-12.2',
      at: '2026-11-20T10:00:00Z',
    }
    renderWithProviders(<PolicyUpdatedNotice />)

    expect(await notice()).toHaveTextContent('Atualizamos a política de privacidade.')
  })

  it('showsNothingWhenThePolicyIsTheAcceptedOne', async () => {
    seedMockFamily()
    renderWithProviders(<PolicyUpdatedNotice />)

    await waitFor(() => expect(mockState.signedIn).toBe(true))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('closeHidesItAndRemembersTheVersion', async () => {
    acceptOldPolicy()
    renderWithProviders(<PolicyUpdatedNotice />)
    await notice()

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(localStorage.getItem(KEY)).not.toBeNull()
  })

  it('staysClosedOnTheNextVisit', async () => {
    acceptOldPolicy()
    localStorage.setItem(KEY, '1')
    renderWithProviders(<PolicyUpdatedNotice />)

    await waitFor(() => expect(mockState.signedIn).toBe(true))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('closesForTheSessionWhenStorageThrows', async () => {
    acceptOldPolicy()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage blocked')
    })
    renderWithProviders(<PolicyUpdatedNotice />)
    await notice()

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
