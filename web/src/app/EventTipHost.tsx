import { Suspense, useEffect, useRef, useState } from 'react'
import { useIntl } from 'react-intl'
import { useMe } from '@/shared/api'
import {
  EventTip,
  eventTips,
  hasSeen,
  isHelpOpen,
  isTipsEnabled,
  markSeen,
  OPEN_DIALOG_SELECTOR,
  seenId,
  setHelpOpen,
  type EventTipId,
} from '@/shared/help'
import { onArrival } from '@/shared/live'
import { onQueued } from '@/shared/offline'

/**
 * Shows the tip of an event the first time it happens for this person on this device. An event
 * that comes while another tip, a tour or a dialog is on the screen is dropped, with no queue.
 */
export function EventTipHost() {
  const intl = useIntl()
  const userId = useMe().data?.user.id
  const [current, setCurrent] = useState<EventTipId | null>(null)
  const currentRef = useRef<EventTipId | null>(null)

  useEffect(() => {
    if (!userId) return
    const show = (id: EventTipId) => {
      if (currentRef.current !== null) return
      if (!isTipsEnabled() || hasSeen(userId, seenId.event(id))) return
      if (isHelpOpen() || document.querySelector(OPEN_DIALOG_SELECTOR) !== null) return
      // Held from now, not from when the lazy tip mounts, so no tour or bubble opens in between.
      currentRef.current = id
      setHelpOpen(true)
      setCurrent(id)
    }
    const offQueued = onQueued(() => {
      if (!navigator.onLine) show('offline')
    })
    const offArrival = onArrival(() => show('live'))
    return () => {
      offQueued()
      offArrival()
      if (currentRef.current !== null) {
        currentRef.current = null
        setHelpOpen(false)
      }
    }
  }, [userId])

  // The live region stays in the page, empty, so a screen reader announces the tip put into it.
  return (
    <div aria-live="polite" aria-atomic="true">
      {userId && current !== null && (
        <Suspense fallback={null}>
          <EventTip
            message={intl.formatMessage({ id: eventTips[current] })}
            onDismiss={() => {
              markSeen(userId, seenId.event(current))
              currentRef.current = null
              setHelpOpen(false)
              setCurrent(null)
            }}
          />
        </Suspense>
      )}
    </div>
  )
}
