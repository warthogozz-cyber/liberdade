import { lazy, Suspense, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { seenId, tours, type TourId, type TourStep } from './catalog'
import { markSeen, useSeen, useTipsEnabled } from './helpStore'
import { helpMessages } from './messages.pt-BR'

registerMessages(helpMessages)

// Kept out of the screen's chunk until a tour really opens.
const Tour = lazy(() => import('./Tour'))

/** The tour can show: no other dialog is on the page and one of its anchors is. */
function canShow(steps: TourStep[]): boolean {
  if (document.querySelector('[role="dialog"]') !== null) return false
  return steps.some((step) => document.querySelector(`[data-tour="${step.anchor}"]`) !== null)
}

/**
 * Whether the tour may show, watching the page until it can. It waits for any open dialog (such as
 * the Feira's location picker) to close; once true it stays true, so a later dialog keeps it open.
 */
function useReadyToShow(steps: TourStep[] | undefined, wanted: boolean): boolean {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    if (!wanted || ready || !steps) return
    const check = () => {
      if (!canShow(steps)) return
      observer.disconnect()
      setReady(true)
    }
    const observer = new MutationObserver(check)
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['role', 'data-tour'],
    })
    check()
    return () => observer.disconnect()
  }, [steps, wanted, ready])
  return ready
}

/**
 * The short tour of a screen: shown once per person and device, unless tips are off. Arriving with
 * `state.forceTour` set to this tour (the "Ver o tour" link) opens it anyway.
 */
export function PageTour({ id }: { id: TourId }) {
  const userId = useMe().data?.user.id
  const location = useLocation()
  const navigate = useNavigate()
  const forced = (location.state as { forceTour?: unknown } | null)?.forceTour === id
  const seen = useSeen(userId, seenId.tour(id))
  const [tipsOn] = useTipsEnabled()
  const [closed, setClosed] = useState(false)
  const steps = tours[id]
  const wanted = !!userId && !!steps && !closed && (forced || (tipsOn && !seen))
  const ready = useReadyToShow(steps, wanted)
  if (!userId || !steps || !wanted || !ready) return null

  return (
    <Suspense fallback={null}>
      <Tour
        steps={steps}
        onClose={() => {
          markSeen(userId, seenId.tour(id))
          setClosed(true)
          // Drop the request, so going back to this page does not open the tour again.
          if (forced) {
            void navigate(
              { pathname: location.pathname, search: location.search, hash: location.hash },
              { replace: true, state: null },
            )
          }
        }}
      />
    </Suspense>
  )
}
