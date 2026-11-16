import type { QueryClient } from '@tanstack/react-query'
import { forgetSelectedWorkspace, resetCsrf } from '@/shared/api'
import { persister } from './persister'

/** Where `returnPath.ts` keeps the page to come back to after a Google re-authentication. */
export const RETURN_PATH_KEY = 'liberdade.returnPath'

/** Where `returnPath.ts` keeps who was signed in when a Google re-authentication began. */
export const REAUTH_USER_KEY = 'liberdade.reauthUserId'

/** Where the invitation page keeps the label of a passkey a new member still has to make. */
export const PENDING_PASSKEY_KEY = 'liberdade.pendingPasskey'

/** Where the invitation page keeps the token of the link it opened. */
export const INVITATION_TOKEN_KEY = 'liberdade.invitationToken'

/**
 * Forgets the session's data on this device: the cache, its saved copy, the CSRF token, the chosen
 * family, the pending return path and an invitation's token. The outbox stays, because each item
 * carries its `userId`. A page that still needs the token stores it again afterwards.
 */
export async function clearSession(queryClient: QueryClient): Promise<void> {
  queryClient.clear()
  resetCsrf()
  forgetSelectedWorkspace()
  try {
    sessionStorage.removeItem(RETURN_PATH_KEY)
    sessionStorage.removeItem(REAUTH_USER_KEY)
    sessionStorage.removeItem(PENDING_PASSKEY_KEY)
    sessionStorage.removeItem(INVITATION_TOKEN_KEY)
  } catch {
    // Nothing to forget without storage.
  }
  await persister.removeClient()
}
