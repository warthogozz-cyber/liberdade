import { lazy, Suspense, useState } from 'react'
import { useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { tipsMessages } from './messages.pt-BR'
import { hasSeenTip, markTipSeen } from './seenTips'
import { isTipsEnabled } from './tipsPreference'
import { tours, type TourId } from './tours'

registerMessages(tipsMessages)

// Kept out of the screen's chunk until a tour really opens.
const Tour = lazy(() => import('./Tour'))

/** The short tour of a screen: shown once per person and device, unless tips are off. */
export function PageTour({ id }: { id: TourId }) {
  const userId = useMe().data?.user.id
  const [closed, setClosed] = useState(false)
  const seenId = `tour-${id}`
  if (!userId || closed || !isTipsEnabled() || hasSeenTip(userId, seenId)) return null

  return (
    <Suspense fallback={null}>
      <Tour
        steps={tours[id]}
        onClose={() => {
          markTipSeen(userId, seenId)
          setClosed(true)
        }}
      />
    </Suspense>
  )
}
