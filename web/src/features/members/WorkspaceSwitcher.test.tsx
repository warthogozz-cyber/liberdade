import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { createFamily, seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

afterEach(cleanup)

describe('WorkspaceSwitcher', () => {
  it('helpText', async () => {
    seedMockFamily()
    createFamily('Família Bia Teste', 'America/Fortaleza')
    renderWithProviders(<WorkspaceSwitcher />, { path: '/mais' })

    expect(
      await screen.findByText('Você faz parte de mais de uma família? Troque aqui.'),
    ).toBeInTheDocument()
  })
})
