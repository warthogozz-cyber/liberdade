import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'
import { useIntl } from 'react-intl'
import { getReserve, planningKeys } from '@/shared/api/planning'
import { onLocalAction } from '@/shared/events'
import { Celebration, CelebrationStack } from '@/shared/motion'
import { celebrationSound } from '@/shared/sound'
import { shouldCelebrateReserve } from './reserveCelebration'
import './register'

/**
 * Invisible until the reserve reaches its target: after a save on this device, looks at the reserve
 * and celebrates once (D12). Queued saves stay silent, and so does a failing call.
 */
export default function ReserveCelebrationWatcher() {
  const queryClient = useQueryClient()
  const intl = useIntl()
  const [months, setMonths] = useState<number | null>(null)
  const done = useCallback(() => setMonths(null), [])

  useEffect(() => {
    // One at a time, so two saves in a row cannot both find the reserve uncelebrated.
    let queue: Promise<void> = Promise.resolve()
    return onLocalAction((action) => {
      if (action.fromQueue || action.type !== 'transaction-saved') return
      const ws = action.workspaceId
      queue = queue
        .then(async () => {
          const view = await queryClient.fetchQuery({
            queryKey: planningKeys.reserve(ws),
            queryFn: () => getReserve(ws),
            staleTime: 0,
          })
          if (shouldCelebrateReserve(ws, view) && view.months !== null) {
            setMonths(Math.floor(view.months))
            celebrationSound(ws)
          }
        })
        // A failed call (offline, the module off) only means no celebration this time.
        .catch(() => undefined)
    })
  }, [queryClient])

  if (months === null) return null
  return (
    <CelebrationStack>
      <Celebration
        icon="🛟"
        message={intl.formatMessage({ id: 'planning.reserve.celebration' }, { months })}
        onDone={done}
      />
    </CelebrationStack>
  )
}
