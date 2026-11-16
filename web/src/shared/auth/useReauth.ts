import { createContext, useCallback, useContext } from 'react'
import { ReauthCancelledError, isReauthRequired } from '@/shared/api'

/** How a re-authentication ended: only `confirmed` lets the action run again. */
export type ReauthOutcome = 'confirmed' | 'cancelled' | 'replaced'

export const ReauthContext = createContext<(() => Promise<ReauthOutcome>) | null>(null)

/**
 * Runs a sensitive action; when the API answers 403 `auth.reauthentication-required`, asks the
 * person to confirm who they are and runs the action once more. A second refusal, or any other
 * error, rejects as it came. Cancelling rejects with `ReauthCancelledError`.
 */
export function useReauth(): <T>(action: () => Promise<T>) => Promise<T> {
  const request = useContext(ReauthContext)
  if (!request) throw new Error('useReauth needs a ReauthProvider above it')
  return useCallback(
    async <T>(action: () => Promise<T>): Promise<T> => {
      try {
        return await action()
      } catch (error) {
        if (!isReauthRequired(error)) throw error
      }
      if ((await request()) !== 'confirmed') throw new ReauthCancelledError()
      return action()
    },
    [request],
  )
}
