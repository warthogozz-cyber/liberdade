import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { isSoundEnabled, useSoundEnabled } from './soundPreference'

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('soundPreference', () => {
  it('offByDefault', () => {
    expect(isSoundEnabled()).toBe(false)
    expect(renderHook(() => useSoundEnabled()).result.current[0]).toBe(false)
  })

  it('persistsOn', () => {
    const { result } = renderHook(() => useSoundEnabled())

    act(() => result.current[1](true))
    expect(result.current[0]).toBe(true)
    expect(localStorage.getItem('liberdade.sound')).toBe('on')
    expect(isSoundEnabled()).toBe(true)

    act(() => result.current[1](false))
    expect(result.current[0]).toBe(false)
    expect(localStorage.getItem('liberdade.sound')).toBeNull()
  })

  it('offWhenStorageThrows', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(isSoundEnabled()).toBe(false)
    const { result } = renderHook(() => useSoundEnabled())
    expect(result.current[0]).toBe(false)
    expect(() => act(() => result.current[1](true))).not.toThrow()
  })
})
