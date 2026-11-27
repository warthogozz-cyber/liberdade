import { lazy, Suspense, useState } from 'react'
import { useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { seenId, tours, type TourId } from './catalog'
import { markSeen, useSeen, useTipsEnabled } from './helpStore'
import { helpMessages } from './messages.pt-BR'

registerMessages(helpMessages)

// Kept out of the screen's chunk until a tour really opens.
const Tour = lazy(() => import('./Tour'))

/** The short tour of a screen: shown once per person and device, unless tips are off. */
export function PageTour({ id }: { id: TourId }) {
  const userId = useMe().data?.user.id
  const seen = useSeen(userId, seenId.tour(id))
  const [tipsOn] = useTipsEnabled()
  const [closed, setClosed] = useState(false)
  const steps = tours[id]
  if (!userId || !steps || closed || !tipsOn || seen) return null

  return (
    <Suspense fallback={null}>
      <Tour
        steps={steps}
        onClose={() => {
          markSeen(userId, seenId.tour(id))
          setClosed(true)
        }}
      />
    </Suspense>
  )
}
