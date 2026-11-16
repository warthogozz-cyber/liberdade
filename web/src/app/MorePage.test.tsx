import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MorePage } from '@/app/MorePage'
import { createFamily, mockState, seedMockFamily } from '@/mocks/data'
import { readSelectedWorkspaceId } from '@/shared/api/workspaceChoice'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('MorePage', () => {
  it('linksToTheFamily', async () => {
    seedMockFamily()
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByRole('link', { name: 'Família' })).toHaveAttribute(
      'href',
      '/mais/familia',
    )
    // One family: nothing to switch.
    expect(screen.queryByText('Trocar de família')).not.toBeInTheDocument()
  })

  it('switcherWithTwoFamilies', async () => {
    seedMockFamily()
    const other = createFamily('Família Bia Teste', 'America/Fortaleza')
    renderWithProviders(<MorePage />, { path: '/mais' })

    expect(await screen.findByText('Trocar de família')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Família Teste/ })).toHaveAttribute(
      'aria-current',
      'true',
    )

    fireEvent.click(screen.getByRole('button', { name: /Família Bia Teste/ }))

    expect(readSelectedWorkspaceId()).toBe(other.id)
    expect(mockState.workspaces).toHaveLength(2)
    expect(screen.getByRole('button', { name: /Família Bia Teste/ })).toHaveAttribute(
      'aria-current',
      'true',
    )
  })
})
