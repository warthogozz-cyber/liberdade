import { lazy, Suspense } from 'react'
import type { MeWorkspace } from '@/shared/api'

const Pending = lazy(() => import('@/features/privacy/Pending'))

/** Stands in for a family's screens (given a family) or for all of them while a deletion is pending. */
export function PendingGate({ workspace }: { workspace?: MeWorkspace }) {
  return (
    <Suspense fallback={null}>
      <Pending workspace={workspace} />
    </Suspense>
  )
}
