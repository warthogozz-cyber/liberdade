import { afterEach, describe, expect, it } from 'vitest'
import { clearInvitationToken, invitationLink, readInvitationToken } from './invitationToken'

const TOKEN = 'A'.repeat(43)

afterEach(() => {
  sessionStorage.clear()
  window.history.replaceState(null, '', '/')
})

describe('invitationToken', () => {
  it('movesFragmentToSession', () => {
    window.history.replaceState(null, '', `/convite#${TOKEN}`)

    expect(readInvitationToken()).toBe(TOKEN)

    // The token left the address bar and sits in this tab's storage.
    expect(window.location.hash).toBe('')
    expect(window.location.pathname).toBe('/convite')
    expect(sessionStorage.getItem('liberdade.invitationToken')).toBe(TOKEN)
    // A second read (a reload, the way back from Google) gives the same answer.
    expect(readInvitationToken()).toBe(TOKEN)

    clearInvitationToken()
    expect(readInvitationToken()).toBeUndefined()
  })

  it('ignoresMalformed', () => {
    window.history.replaceState(null, '', '/convite#nao-e-um-token')

    expect(readInvitationToken()).toBeUndefined()

    expect(window.location.hash).toBe('')
    expect(sessionStorage.getItem('liberdade.invitationToken')).toBeNull()
  })

  it('putsTheTokenInTheFragmentOfTheLink', () => {
    const link = invitationLink(TOKEN)

    expect(link).toBe(`${window.location.origin}/convite#${TOKEN}`)
    expect(new URL(link).search).toBe('')
  })
})
