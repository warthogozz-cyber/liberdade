const KEY = 'liberdade.invitationToken'

/** The API's tokens: 32 random bytes, base64url without padding. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/

function isToken(value: string | null | undefined): value is string {
  return typeof value === 'string' && TOKEN_PATTERN.test(value)
}

function readStored(): string | undefined {
  try {
    const stored = sessionStorage.getItem(KEY)
    return isToken(stored) ? stored : undefined
  } catch {
    return undefined
  }
}

/**
 * The invitation's token, from the link's fragment. The fragment never reaches the server; it moves
 * to this tab's `sessionStorage` and leaves the address bar, so it stays out of history and
 * screenshots. Without a fragment, what an earlier read stored (a trip to Google, a reload).
 * Reading twice gives the same answer. A fragment that is not a token is dropped and ignored.
 */
export function readInvitationToken(): string | undefined {
  const fragment = window.location.hash.replace(/^#/, '')
  if (fragment === '') return readStored()
  window.history.replaceState(
    window.history.state,
    '',
    `${window.location.pathname}${window.location.search}`,
  )
  if (!isToken(fragment)) return undefined
  try {
    sessionStorage.setItem(KEY, fragment)
  } catch {
    // Without storage the token lives only in the page that read it.
  }
  return fragment
}

/** Forgets the stored token: the invitation was used, or is no use any more. */
export function clearInvitationToken(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // Nothing to forget without storage.
  }
}

/** The link a person opens to accept: the token rides in the fragment, never in the path or query. */
export function invitationLink(token: string): string {
  return `${window.location.origin}/convite#${token}`
}
