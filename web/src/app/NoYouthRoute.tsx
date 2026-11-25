import { Navigate, Outlet } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'

/** Wraps the screens a Youth never sees: a Youth who reaches one by its address goes back to More. */
export function NoYouthRoute() {
  const workspace = useCurrentWorkspace()
  if (workspace?.role === 'YOUTH') return <Navigate to="/mais" replace />
  return <Outlet />
}
