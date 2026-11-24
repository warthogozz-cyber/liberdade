import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
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
})
