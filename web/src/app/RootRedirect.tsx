import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Navigate } from 'react-router'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { takeReauthUser, takeReturnPath } from '@/shared/auth/returnPath'
import { broadcastSignedIn, clearSession } from '@/shared/offline'

/**
 * `/` is where a sign-in lands. It tells the other tabs who signed in, then opens the page a
 * re-authentication or an invitation came from, or the home page, or the welcome page while the
 * person has no family. A Google re-authentication that turned out to be another person clears this
 * tab and starts over at `/inicio`.
 */
export function RootRedirect() {
  const queryClient = useQueryClient()
  const workspace = useCurrentWorkspace()
  const me = useMe()
  const userId = me.data?.user.id
  // Not the copy restored from the device: a fetch of `me` is still on its way while it runs.
  const fresh = !me.isFetching
  // Taken once: both are gone from the storage after the first read.
  const [taken] = useState(() => ({ returnPath: takeReturnPath(), reauthUser: takeReauthUser() }))
  const changed =
    taken.reauthUser !== undefined && fresh && userId !== undefined && userId !== taken.reauthUser

  useEffect(() => {
    if (!userId || !fresh) return
    broadcastSignedIn(userId)
    if (!changed) return
    void clearSession(queryClient)
      .catch((error: unknown) => console.error('Could not clear the session', error))
      .then(() => window.location.assign('/inicio'))
  }, [userId, fresh, changed, queryClient])

  // After a re-authentication, wait for the fresh `me` to know whose session this is.
  if (changed || (taken.reauthUser !== undefined && !fresh)) return null

  const home = workspace ? '/inicio' : '/boas-vindas'
  // An invitation's page is for people without a family too; the others need one.
  const target =
    taken.returnPath && (taken.returnPath.startsWith('/convite') || workspace)
      ? taken.returnPath
      : home
  return <Navigate to={target} replace />
}
