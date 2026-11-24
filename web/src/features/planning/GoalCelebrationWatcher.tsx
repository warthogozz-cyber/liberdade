import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { getGoals, planningKeys } from '@/shared/api/planning'
import { onLocalAction } from '@/shared/events'
import { celebrationSound } from '@/shared/sound'
import { celebrateNew, useCelebrationQueue } from './celebration'
import { GoalCelebrations } from './GoalCelebration'
import './register'

/**
 * Invisible until a goal is reached: after an expense or a contribution saved on this device, looks
 * at the goals and celebrates the ones reached recently that were not celebrated yet (D20). Queued
 * saves stay silent, and so does a failing call.
 */
export default function GoalCelebrationWatcher() {
  const queryClient = useQueryClient()
  const { items, add, remove } = useCelebrationQueue()

  useEffect(() => {
    // One at a time, so two saves in a row cannot both find the goal uncelebrated.
    let queue: Promise<void> = Promise.resolve()
    return onLocalAction((action) => {
      if (action.fromQueue) return
      const ws = action.workspaceId
      queue = queue
        .then(async () => {
          const view = await queryClient.fetchQuery({
            queryKey: planningKeys.goals(ws),
            queryFn: () => getGoals(ws),
            staleTime: 0,
          })
          const reached = celebrateNew(ws, view.goals, new Date())
          if (reached.length > 0) {
            add(reached)
            celebrationSound(ws)
          }
        })
        // A failed call (offline, the module off) only means no celebration this time.
        .catch(() => undefined)
    })
  }, [queryClient, add])

  return <GoalCelebrations items={items} onDone={remove} />
}
