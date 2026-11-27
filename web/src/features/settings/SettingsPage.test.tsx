import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState } from '@/mocks/data'
import { hasSeen, markSeen } from '@/shared/help'
import { playAchievement } from '@/shared/sound/playAchievement'
import { unlockSound } from '@/shared/sound/unlockSound'
import { renderWithProviders } from '@/shared/testing'
import { SettingsPage } from './SettingsPage'

vi.mock('@/shared/sound/playAchievement', () => ({
  playAchievement: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/shared/sound/unlockSound', () => ({
  unlockSound: vi.fn(),
  armSoundUnlock: vi.fn(),
}))

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.mocked(playAchievement).mockClear()
  vi.mocked(unlockSound).mockClear()
})

describe('SettingsPage', () => {
  it('switchStartsOff', () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    expect(screen.getByRole('heading', { name: 'Ajustes' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Sons de conquista' })).toHaveAttribute(
      'aria-checked',
      'false',
    )
    expect(
      screen.getByText('Um som curto quando uma meta é atingida ou a reserva fica completa.'),
    ).toBeInTheDocument()
  })

  it('toggleAndListen', () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    fireEvent.click(screen.getByRole('switch', { name: 'Sons de conquista' }))
    expect(screen.getByRole('switch', { name: 'Sons de conquista' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    expect(localStorage.getItem('liberdade.sound')).toBe('on')

    fireEvent.click(screen.getByRole('button', { name: 'Ouvir' }))
    expect(playAchievement).toHaveBeenCalledOnce()
  })

  it('listensEvenWhenOff', () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    fireEvent.click(screen.getByRole('button', { name: 'Ouvir' }))
    expect(playAchievement).toHaveBeenCalledOnce()
  })

  it('unlocksTheSoundOnTheGestureThatTurnsItOn', () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    fireEvent.click(screen.getByRole('switch', { name: 'Sons de conquista' }))
    expect(unlockSound).toHaveBeenCalledOnce()

    fireEvent.click(screen.getByRole('switch', { name: 'Sons de conquista' }))
    expect(unlockSound).toHaveBeenCalledOnce()
  })

  it('unlocksTheSoundWhenListening', () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    fireEvent.click(screen.getByRole('button', { name: 'Ouvir' }))
    expect(unlockSound).toHaveBeenCalledOnce()
  })

  it('tipsSwitchIsOnAndTurnsOff', () => {
    localStorage.removeItem('liberdade.tips')
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    expect(screen.getByRole('heading', { name: 'Dicas' })).toBeInTheDocument()
    const tips = screen.getByRole('switch', { name: 'Mostrar dicas' })
    expect(tips).toHaveAttribute('aria-checked', 'true')

    fireEvent.click(tips)
    expect(screen.getByRole('switch', { name: 'Mostrar dicas' })).toHaveAttribute(
      'aria-checked',
      'false',
    )
    expect(localStorage.getItem('liberdade.tips')).toBe('off')
  })

  it('reviewTipsResetsAndTurnsOn', async () => {
    const me = mockState.user.id
    markSeen(me, 'home')
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })
    expect(screen.getByRole('switch', { name: 'Mostrar dicas' })).toHaveAttribute(
      'aria-checked',
      'false',
    )

    const review = screen.getByRole('button', { name: 'Rever dicas' })
    await waitFor(() => expect(review).toBeEnabled())
    fireEvent.click(review)

    await waitFor(() => expect(hasSeen(me, 'home')).toBe(false))
    expect(screen.getByRole('switch', { name: 'Mostrar dicas' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    expect(screen.getByRole('status')).toHaveTextContent('Pronto. As dicas vão aparecer de novo.')
  })

  it('reviewedClearsWhenTipsTurnedOff', async () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    const review = screen.getByRole('button', { name: 'Rever dicas' })
    await waitFor(() => expect(review).toBeEnabled())
    fireEvent.click(review)
    expect(screen.getByRole('status')).toHaveTextContent('Pronto. As dicas vão aparecer de novo.')

    fireEvent.click(screen.getByRole('switch', { name: 'Mostrar dicas' }))
    expect(screen.getByRole('switch', { name: 'Mostrar dicas' })).toHaveAttribute(
      'aria-checked',
      'false',
    )
    const status = screen.getByRole('status')
    expect(status).toBeInTheDocument()
    expect(status).not.toHaveTextContent('Pronto. As dicas vão aparecer de novo.')
  })

  it('reviewTipsKeepsOtherPeople', async () => {
    markSeen('someone-else', 'home')
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    const review = screen.getByRole('button', { name: 'Rever dicas' })
    await waitFor(() => expect(review).toBeEnabled())
    fireEvent.click(review)

    await screen.findByRole('status')
    expect(hasSeen('someone-else', 'home')).toBe(true)
  })

  it('motionLine', () => {
    renderWithProviders(<SettingsPage />, { path: '/mais/ajustes' })

    expect(
      screen.getByText('O app segue a opção Reduzir movimento do seu aparelho.'),
    ).toBeInTheDocument()
  })
})
