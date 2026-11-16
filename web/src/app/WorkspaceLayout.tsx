import { Navigate, Outlet } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'

/** The screens that need a family. Without one, the person goes to the welcome page first. */
export function WorkspaceLayout() {
  const workspace = useCurrentWorkspace()
  if (!workspace) return <Navigate to="/boas-vindas" replace />
  return <Outlet />
}
