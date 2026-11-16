import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { InvitePage } from './InvitePage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  Reflect.deleteProperty(navigator, 'clipboard')
})

function renderInvite() {
  return renderWithProviders(<InvitePage />, { path: '/mais/familia/convidar' })
}

const roleOptions = () =>
  Array.from((screen.getByLabelText('Papel') as HTMLSelectElement).options).map(
    (option) => option.value,
  )

describe('InvitePage', () => {
  it('adultCannotPickOwner', async () => {
    seedMockFamily().role = 'ADULT'
    renderInvite()

    await screen.findByLabelText('Papel')
    expect(roleOptions()).toEqual(['ADULT', 'YOUTH', 'VIEWER'])
    expect(screen.queryByRole('option', { name: 'Dono' })).not.toBeInTheDocument()
  })

  it('ownerCanPickOwner', async () => {
    seedMockFamily()
    renderInvite()

    await screen.findByLabelText('Papel')
    expect(roleOptions()).toContain('OWNER')
  })

  it('youthNeedsConsent', async () => {
    seedMockFamily()
    renderInvite()

    fireEvent.change(await screen.findByLabelText('Apelido'), { target: { value: 'Caçula' } })
    fireEvent.change(screen.getByLabelText('Papel'), { target: { value: 'YOUTH' } })

    const create = screen.getByRole('button', { name: 'Criar convite' })
    expect(create).toBeDisabled()
    fireEvent.click(screen.getByRole('checkbox'))
    expect(create).toBeEnabled()
  })

  it('showsLinkWithFragment', async () => {
    seedMockFamily()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    renderInvite()

    fireEvent.change(await screen.findByLabelText('Apelido'), { target: { value: 'Bia' } })
    fireEvent.click(screen.getByRole('button', { name: 'Criar convite' }))

    const link = (await screen.findByLabelText('Link do convite')) as HTMLInputElement
    expect(link.value).toMatch(/\/convite#[A-Za-z0-9_-]{43}$/)
    expect(new URL(link.value).search).toBe('')
    expect(screen.getByText('O link vale por 7 dias e funciona uma vez só.')).toBeInTheDocument()
    // No Share button where the browser has no share sheet.
    expect(screen.queryByRole('button', { name: 'Compartilhar' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Copiar link' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(link.value))
    expect(await screen.findByText('Link copiado.')).toBeInTheDocument()
  })
})
