import type { QueryClient } from '@tanstack/react-query'
import { resetCsrf } from '@/shared/api'
import { persister } from './persister'

/**
 * Forgets the session's data on this device: the cache, its saved copy and the CSRF token. The
 * outbox stays, because each item carries its `userId`.
 */
export async function clearSession(queryClient: QueryClient): Promise<void> {
  queryClient.clear()
  resetCsrf()
  await persister.removeClient()
}
