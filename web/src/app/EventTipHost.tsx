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
      currentRef.current = id
      setCurrent(id)
    }
    const offQueued = onQueued(() => {
      if (!navigator.onLine) show('offline')
    })
    const offArrival = onArrival(() => show('live'))
    return () => {
      offQueued()
      offArrival()
    }
  }, [userId])

  if (!userId || current === null) return null
  return (
    <Suspense fallback={null}>
      <EventTip
        message={intl.formatMessage({ id: eventTips[current] })}
        onDismiss={() => {
          markSeen(userId, seenId.event(current))
          currentRef.current = null
          setCurrent(null)
        }}
      />
    </Suspense>
  )
}
