import { useEffect, useState } from 'react'
import { Navigate } from 'react-router'
import { useCurrentWorkspace, useMe } from '@/shared/api'
import { takeReturnPath } from '@/shared/auth'
import { broadcastSignedIn } from '@/shared/offline'

/**
 * `/` is where a sign-in lands. It tells the other tabs who signed in, then opens the page a
 * re-authentication came from, or the home page, or the welcome page while the person has no family.
 */
export function RootRedirect() {
  const workspace = useCurrentWorkspace()
  const userId = useMe().data?.user.id
  // Taken once: the path is gone from the storage after the first read.
  const [returnPath] = useState(takeReturnPath)

  useEffect(() => {
    if (userId) broadcastSignedIn(userId)
  }, [userId])

  const home = workspace ? '/inicio' : '/boas-vindas'
  return <Navigate to={workspace && returnPath ? returnPath : home} replace />
}
