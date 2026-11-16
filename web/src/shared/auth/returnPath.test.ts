import { describe, expect, it } from 'vitest'
import { saveReturnPath, takeReturnPath } from './returnPath'

describe('returnPath', () => {
  it('acceptsListedPaths', () => {
    saveReturnPath('/mais/privacidade')
    expect(takeReturnPath()).toBe('/mais/privacidade')

    for (const path of [
      'https://evil.example.com',
      '//evil.example.com',
      '/qualquer',
      '/conviteX',
      '/mais/familia/../x',
      '/mais//familia',
      '/mais/familia\\x',
      '/convite\n',
      '/convite:8080',
      'mais/familia',
      '/%2F%2Fevil.example.com',
      '/mais%2Ffamilia',
      '/\\evil.example.com',
    ]) {
      saveReturnPath(path)
      expect(takeReturnPath(), path).toBeUndefined()
    }
  })

  it('keeps the query and the hash of a listed path', () => {
    saveReturnPath('/convite?a=1#token')
    expect(takeReturnPath()).toBe('/convite?a=1#token')
  })

  it('takeClears', () => {
    saveReturnPath('/mais/modulos')

    expect(takeReturnPath()).toBe('/mais/modulos')
    expect(takeReturnPath()).toBeUndefined()
  })

  it('removes a value that is not valid, and never picks one from another tab', () => {
    sessionStorage.setItem('liberdade.returnPath', 'https://evil.example.com')
    localStorage.setItem('liberdade.returnPath', '/mais/familia')

    expect(takeReturnPath()).toBeUndefined()
    expect(sessionStorage.getItem('liberdade.returnPath')).toBeNull()
  })
})
