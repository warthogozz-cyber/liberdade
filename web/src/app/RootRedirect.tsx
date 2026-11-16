import { Navigate } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'

/** `/` opens the home page, or the welcome page while the person has no family yet. */
export function RootRedirect() {
  const workspace = useCurrentWorkspace()
  return <Navigate to={workspace ? '/inicio' : '/boas-vindas'} replace />
}
