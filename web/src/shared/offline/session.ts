import type { QueryClient } from '@tanstack/react-query'
import { forgetSelectedWorkspace, resetCsrf } from '@/shared/api'
import { persister } from './persister'

/** Where `returnPath.ts` keeps the page to come back to after a Google re-authentication. */
export const RETURN_PATH_KEY = 'liberdade.returnPath'

/** Where `returnPath.ts` keeps who was signed in when a Google re-authentication began. */
export const REAUTH_USER_KEY = 'liberdade.reauthUserId'

/**
 * Forgets the session's data on this device: the cache, its saved copy, the CSRF token, the chosen
 * family and the pending return path. The outbox stays, because each item carries its `userId`.
 */
export async function clearSession(queryClient: QueryClient): Promise<void> {
  queryClient.clear()
  resetCsrf()
  forgetSelectedWorkspace()
  try {
    sessionStorage.removeItem(RETURN_PATH_KEY)
    sessionStorage.removeItem(REAUTH_USER_KEY)
  } catch {
    // Nothing to forget without storage.
  }
  await persister.removeClient()
}
