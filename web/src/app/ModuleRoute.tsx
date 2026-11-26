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
 * the module on), so the server is asked once more before the redirect. One attempt, no retries:
 * without a connection the saved list decides at once.
 */
export function ModuleRoute({ module, children }: { module: ModuleKey; children: ReactNode }) {
  const workspace = useCurrentWorkspace()
  const workspaceId = workspace?.id
  const modules = useModules(workspaceId)
  const queryClient = useQueryClient()
  const [rechecked, setRechecked] = useState(false)
  const enabled = modules.data?.find((each) => each.key === module)?.enabled === true
  const cachedOff = modules.isSuccess && !enabled

  useEffect(() => {
    if (!cachedOff || rechecked || workspaceId === undefined) return
    queryClient
      .fetchQuery({
        queryKey: queryKeys.modules(workspaceId),
        queryFn: () => listModules(workspaceId),
        staleTime: 0,
        retry: false,
      })
      .catch(() => undefined)
      .finally(() => setRechecked(true))
  }, [cachedOff, rechecked, workspaceId, queryClient])

  if (modules.isPending) return null
  if (enabled) return children
  if (modules.isSuccess && !rechecked) return null
  return <Navigate to="/inicio" replace />
}
