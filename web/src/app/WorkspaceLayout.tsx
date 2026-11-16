import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router'
import { prefetchWorkspace, useCurrentWorkspace } from '@/shared/api'
import { currentMonth } from '@/shared/i18n'

/** The screens that need a family. Without one, the person goes to the welcome page first. */
export function WorkspaceLayout() {
  const workspace = useCurrentWorkspace()
  const queryClient = useQueryClient()
  const workspaceId = workspace?.id
  const timezone = workspace?.timezone

  // Loads what the screens read as soon as the family is known, so it is cached if the signal drops.
  useEffect(() => {
    if (workspaceId === undefined || timezone === undefined) return
    void prefetchWorkspace(queryClient, workspaceId, currentMonth(new Date(), timezone))
  }, [queryClient, workspaceId, timezone])

  if (!workspace) return <Navigate to="/boas-vindas" replace />
  return <Outlet />
}
