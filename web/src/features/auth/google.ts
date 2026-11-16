import type { QueryClient } from '@tanstack/react-query'
import { API_URL } from '@/shared/api'
import { clearSession } from '@/shared/offline'

/**
 * Forgets the previous session's data, then leaves for the API's Google sign-in, so the new session
 * starts with an empty cache (Review Focus 2).
 */
export async function startGoogleSignIn(
  queryClient: QueryClient,
  assign: (url: string) => void = (url) => window.location.assign(url),
): Promise<void> {
  await clearSession(queryClient)
  assign(`${API_URL}/oauth2/authorization/google`)
}
