import { createElement } from 'react'
import { act, render, renderHook, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  clearSeen,
  hasSeen,
  isHelpOpen,
  markSeen,
  resetAllSeen,
  setHelpOpen,
  useSeen,
  useTipsEnabled,
} from './helpStore'

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
  setHelpOpen(false)
})

describe('helpStore', () => {
  it('markThenHasSeen', () => {
    expect(hasSeen('u1', 'home')).toBe(false)
    markSeen('u1', 'home')
    expect(hasSeen('u1', 'home')).toBe(true)
    expect(localStorage.getItem('liberdade.tip:u1:home')).toBe('1')
  })

  it('seenIsPerPerson', () => {
    markSeen('u1', 'home')
    expect(hasSeen('u2', 'home')).toBe(false)
  })

  it('resetOnlyThisPerson', () => {
    localStorage.setItem('liberdade.tips', 'off')
    markSeen('u1', 'home')
    markSeen('u1', 'tour-markets')
    markSeen('u2', 'home')
    resetAllSeen('u1')
    expect(hasSeen('u1', 'home')).toBe(false)
    expect(hasSeen('u1', 'tour-markets')).toBe(false)
    expect(hasSeen('u2', 'home')).toBe(true)
    expect(localStorage.getItem('liberdade.tip:u2:home')).toBe('1')
    expect(localStorage.getItem('liberdade.tip:u1:home')).toBeNull()
    expect(localStorage.getItem('liberdade.tips')).toBe('off')
  })

  it('clearSeenOne', () => {
    markSeen('u1', 'home')
    markSeen('u1', 'tour-markets')
    clearSeen('u1', 'tour-markets')
    expect(localStorage.getItem('liberdade.tip:u1:tour-markets')).toBeNull()
    expect(localStorage.getItem('liberdade.tip:u1:home')).toBe('1')
    expect(hasSeen('u1', 'tour-markets')).toBe(false)
  })

  it('storageThrowsKeepsInMemory', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => markSeen('u9', 'memory')).not.toThrow()
    expect(hasSeen('u9', 'memory')).toBe(true)
  })

  it('useSeenRerenders', () => {
    function Probe() {
      return createElement('p', null, useSeen('u1', 'home') ? 'seen' : 'new')
    }
    render(createElement(Probe))
    expect(screen.getByText('new')).toBeInTheDocument()
    act(() => markSeen('u1', 'home'))
    expect(screen.getByText('seen')).toBeInTheDocument()
  })

  it('storageEventRerenders', () => {
    const { result } = renderHook(() => useSeen('u1', 'home'))
    expect(result.current).toBe(false)
    act(() => {
      localStorage.setItem('liberdade.tip:u1:home', '1')
      window.dispatchEvent(new StorageEvent('storage', { key: 'liberdade.tip:u1:home' }))
    })
    expect(result.current).toBe(true)
  })

  it('tipsToggleIsShared', () => {
    localStorage.removeItem('liberdade.tips') // the test setup turns tips off
    const a = renderHook(() => useTipsEnabled())
    const b = renderHook(() => useTipsEnabled())
    expect(b.result.current[0]).toBe(true)
    act(() => a.result.current[1](false))
    expect(b.result.current[0]).toBe(false)
    expect(localStorage.getItem('liberdade.tips')).toBe('off')
    act(() => b.result.current[1](true))
    expect(a.result.current[0]).toBe(true)
    expect(localStorage.getItem('liberdade.tips')).toBeNull()
  })

  it('helpOpenFlag', () => {
    setHelpOpen(true)
    expect(isHelpOpen()).toBe(true)
    setHelpOpen(false)
    expect(isHelpOpen()).toBe(false)
  })
})
