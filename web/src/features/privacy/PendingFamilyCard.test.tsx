import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { createFamily, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { PendingFamilyCard } from './PendingFamilyCard'

afterEach(cleanup)

describe('PendingFamilyCard', () => {
  it('showsTheSwitcherHelp', async () => {
    const workspace = seedMockFamily()
    createFamily('Família Bia Teste', 'America/Fortaleza')
    renderWithProviders(
      <PendingFamilyCard
        workspace={{ ...workspace, deletionScheduledFor: '2026-12-20T00:00:00Z' }}
      />,
      { path: '/' },
    )

    expect(
      await screen.findByText('Você faz parte de mais de uma família? Troque aqui.'),
    ).toBeInTheDocument()
  })
})
