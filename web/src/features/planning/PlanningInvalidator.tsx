import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { planningKeys } from '@/shared/api/planning'
import { onLocalAction } from '@/shared/events'

/** Invisible: refreshes what planning shows after any local action, queued ones included. */
export default function PlanningInvalidator() {
  const queryClient = useQueryClient()
  useEffect(
    () =>
      onLocalAction((action) => {
        void queryClient.invalidateQueries({ queryKey: planningKeys.all(action.workspaceId) })
      }),
    [queryClient],
  )
  return null
}
