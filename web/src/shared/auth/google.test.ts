import { describe, expect, it, vi } from 'vitest'
import { startGoogleSignIn } from './google'
import { takeReturnPath } from './returnPath'

describe('startGoogleSignIn', () => {
  it('goes to the Google sign-in of the API', () => {
    const assign = vi.fn<(url: string) => void>()

    startGoogleSignIn(undefined, assign)

    expect(assign).toHaveBeenCalledExactlyOnceWith(
      'http://localhost:8080/oauth2/authorization/google',
    )
  })

  it('asks for a re-authentication and remembers where to come back to', () => {
    const assign = vi.fn<(url: string) => void>()

    startGoogleSignIn({ reauth: true, returnTo: '/mais/familia' }, assign)

    expect(assign).toHaveBeenCalledExactlyOnceWith(
      'http://localhost:8080/oauth2/authorization/google?reauth=true',
    )
    expect(takeReturnPath()).toBe('/mais/familia')
  })
})
