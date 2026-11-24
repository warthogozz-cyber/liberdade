import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useCurrentWorkspace, useModules, type ModuleKey } from '@/shared/api'

/**
 * Wraps the screens of one module: with the module off, an address typed by hand goes to the home
 * page. It renders nothing while the module list loads, so the screen never flashes before the
 * redirect.
 */
export function ModuleRoute({ module, children }: { module: ModuleKey; children: ReactNode }) {
  const workspace = useCurrentWorkspace()
  const modules = useModules(workspace?.id)
  if (modules.isPending) return null
  const enabled = modules.data?.find((each) => each.key === module)?.enabled === true
  if (!enabled) return <Navigate to="/inicio" replace />
  return children
}
