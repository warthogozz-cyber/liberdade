import { Navigate, Outlet } from 'react-router'
import { useCurrentWorkspace, useModules } from '@/shared/api'

/**
 * Wraps every planning screen: with the module off, an address typed by hand goes to the home page.
 * It renders nothing while the module list loads, so the hub never flashes before the redirect.
 */
export function PlanningModuleRoute() {
  const workspace = useCurrentWorkspace()
  const modules = useModules(workspace?.id)
  if (modules.isPending) return null
  const enabled = modules.data?.find((module) => module.key === 'planning')?.enabled === true
  if (!enabled) return <Navigate to="/inicio" replace />
  return <Outlet />
}
