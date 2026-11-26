import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Navigate } from 'react-router'
import {
  listModules,
  queryKeys,
  useCurrentWorkspace,
  useModules,
  type ModuleKey,
} from '@/shared/api'

/**
 * Wraps the screens of one module: with the module off, an address typed by hand goes to the home
 * page. It renders nothing while the module list loads, so the screen never flashes before the
 * redirect.
 *
 * A list that says off may be old (saved on the device, or read seconds before the family switched
 * the module on), so the server is asked once more before the redirect. A list saved on the device
 * before this page load that says on is asked about too, before the screen mounts: another device
 * may have switched the module off, and the screen would fire requests the server refuses. One
 * attempt, no retries: without a connection the saved list decides at once.
 */
/** Lists fetched before this moment came from the device, not from this session. */
const PAGE_LOADED_AT = Date.now()

export function ModuleRoute({ module, children }: { module: ModuleKey; children: ReactNode }) {
  const workspace = useCurrentWorkspace()
  const workspaceId = workspace?.id
  const modules = useModules(workspaceId)
  const queryClient = useQueryClient()
  const [rechecked, setRechecked] = useState(false)
  const enabled = modules.data?.find((each) => each.key === module)?.enabled === true
  const savedOnDevice = modules.dataUpdatedAt < PAGE_LOADED_AT
  const needsRecheck = modules.isSuccess && (!enabled || savedOnDevice)

  useEffect(() => {
    if (!needsRecheck || rechecked || workspaceId === undefined) return
    queryClient
      .fetchQuery({
        queryKey: queryKeys.modules(workspaceId),
        queryFn: () => listModules(workspaceId),
        staleTime: 0,
        retry: false,
      })
      .catch(() => undefined)
      .finally(() => setRechecked(true))
  }, [needsRecheck, rechecked, workspaceId, queryClient])

  if (modules.isPending) return null
  if (needsRecheck && !rechecked) return null
  if (enabled) return children
  return <Navigate to="/inicio" replace />
}
