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
import { useToastShowing } from '@/shared/ui'

/**
 * Shows the tip of an event the first time it happens for this person on this device. An event
 * that comes while another tip, a tour or a dialog is on the screen is dropped, with no queue.
 * A dialog opened later hides it the same way. A notice (such as "Desfazer") uses the same place at the bottom, so the tip waits, still holding
 * the slot, while one is on the screen, and comes back when it goes.
 */
export function EventTipHost() {
  const intl = useIntl()
  const userId = useMe().data?.user.id
  const [current, setCurrent] = useState<EventTipId | null>(null)
  const currentRef = useRef<EventTipId | null>(null)
  const toastShowing = useToastShowing()

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
      // A sign-out drops the tip too, so it cannot come back, without the slot, on the next sign-in.
      setCurrent(null)
    }
  }, [userId])

  // A dialog opened after the tip covers it: the tip steps aside, still holding the slot, until it closes.
  const [dialogOpen, setDialogOpen] = useState(false)
  useEffect(() => {
    if (current === null) return
    const check = () => setDialogOpen(document.querySelector(OPEN_DIALOG_SELECTOR) !== null)
    check()
    const observer = new MutationObserver(check)
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['role'],
    })
    return () => observer.disconnect()
  }, [current])

  // The live region stays in the page, empty, so a screen reader announces the tip put into it.
  return (
    <div aria-live="polite" aria-atomic="true">
      {userId && current !== null && !toastShowing && !dialogOpen && (
        <Suspense fallback={null}>
          <EventTip
            message={intl.formatMessage({ id: eventTips[current] })}
            onDismiss={() => {
              // Only the tip that still holds the slot lets it go, so the count never drops twice.
              if (currentRef.current === null) return
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
