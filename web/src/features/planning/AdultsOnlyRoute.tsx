import { Navigate, Outlet } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'

/** Wraps the screens a Youth never sees: a Youth who reaches one by its address goes to the hub. */
export function AdultsOnlyRoute() {
  const workspace = useCurrentWorkspace()
  if (workspace?.role === 'YOUTH') return <Navigate to="/planejar" replace />
  return <Outlet />
}
