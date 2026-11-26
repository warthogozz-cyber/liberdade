import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { PlanRequired } from './PlanRequired'

afterEach(cleanup)

describe('PlanRequired', () => {
  it('names the feature and points to the plan screen', async () => {
    seedMockFamily()
    renderWithProviders(<PlanRequired title="Feira" />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Feira' })).toBeInTheDocument()
    expect(screen.getByText('Este recurso faz parte do plano Família.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver o plano Família' })).toHaveAttribute(
      'href',
      '/mais/plano',
    )
    expect(document.title).toContain('Feira')
  })

  it('tells someone who is not the owner who can change the plan', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'ADULT'
    renderWithProviders(<PlanRequired title="Feira" />)

    expect(
      await screen.findByText('Só quem é dono da família pode mudar o plano.'),
    ).toBeInTheDocument()
  })

  it('leaves that line out for the owner', async () => {
    seedMockFamily()
    renderWithProviders(<PlanRequired title="Feira" />)

    await screen.findByText('Este recurso faz parte do plano Família.')
    expect(screen.queryByText('Só quem é dono da família pode mudar o plano.')).toBeNull()
  })
})
