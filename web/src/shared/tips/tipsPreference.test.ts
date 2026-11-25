import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { isTipsEnabled, useTipsEnabled } from './tipsPreference'

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('tipsPreference', () => {
  it('onByDefault', () => {
    localStorage.removeItem('liberdade.tips')
    expect(isTipsEnabled()).toBe(true)
    expect(renderHook(() => useTipsEnabled()).result.current[0]).toBe(true)
  })

  it('turningOffStoresOff', () => {
    const { result } = renderHook(() => useTipsEnabled())

    act(() => result.current[1](false))
    expect(result.current[0]).toBe(false)
    expect(localStorage.getItem('liberdade.tips')).toBe('off')
    expect(isTipsEnabled()).toBe(false)

    act(() => result.current[1](true))
    expect(result.current[0]).toBe(true)
    expect(isTipsEnabled()).toBe(true)
  })

  it('onWhenStorageThrows', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(isTipsEnabled()).toBe(true)
    const { result } = renderHook(() => useTipsEnabled())
    expect(result.current[0]).toBe(true)
    expect(() => act(() => result.current[1](false))).not.toThrow()
  })
})
