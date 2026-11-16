import { API_URL } from '@/shared/api'
import { saveReturnPath } from './returnPath'

interface GoogleSignInOptions {
  /** Asks Google to show the account chooser again, for a sensitive action (US-01). */
  reauth?: boolean
  /** A listed page to come back to once the sign-in is over. */
  returnTo?: string
}

/**
 * Leaves for the API's Google sign-in. A plain sign-in empties the previous session's data first
 * (the caller's job, Review Focus 2); a re-authentication keeps it.
 */
export function startGoogleSignIn(
  { reauth = false, returnTo }: GoogleSignInOptions = {},
  assign: (url: string) => void = (url) => window.location.assign(url),
): void {
  if (returnTo) saveReturnPath(returnTo)
  assign(`${API_URL}/oauth2/authorization/google${reauth ? '?reauth=true' : ''}`)
}
