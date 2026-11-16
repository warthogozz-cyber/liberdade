import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router'
import { PendingGate } from '@/app/PendingGate'
import { prefetchWorkspace, useCurrentWorkspace } from '@/shared/api'
import { currentMonth } from '@/shared/i18n'
import { saveQueryCacheNow } from '@/shared/offline'

/** The screens that need a family. Without one, the person goes to the welcome page first. */
export function WorkspaceLayout() {
  const workspace = useCurrentWorkspace()
  const queryClient = useQueryClient()
  const workspaceId = workspace?.id
  const timezone = workspace?.timezone

  // Loads what the screens read as soon as the family is known, so it is cached if the signal drops,
  // and saves it to the device at once, so a reload offline right after still finds it.
  useEffect(() => {
    if (workspaceId === undefined || timezone === undefined) return
    void prefetchWorkspace(queryClient, workspaceId, currentMonth(new Date(), timezone)).then(() =>
      saveQueryCacheNow(queryClient),
    )
  }, [queryClient, workspaceId, timezone])

  if (!workspace) return <Navigate to="/boas-vindas" replace />
  // Every route of the family gives way to the card while its deletion is pending.
  if (workspace.deletionScheduledFor) return <PendingGate workspace={workspace} />
  return <Outlet />
}
