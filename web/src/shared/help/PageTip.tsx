import { lazy, Suspense, useEffect, useState } from 'react'
import { useIntl } from 'react-intl'
import { useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { screenTips, seenId, type TipId } from './catalog'
import { markSeen, setHelpOpen, useHelpOpen, useSeen, useTipsEnabled } from './helpStore'
import { helpMessages } from './messages.pt-BR'

registerMessages(helpMessages)

// Kept out of the screen's chunk until a tip really shows.
const TipBubble = lazy(() => import('./TipBubble'))

/**
 * The first visit tip of a screen: shown once per person and device, unless tips are off. It waits
 * while another tip or tour holds the help slot; once it shows, its own hold does not hide it.
 */
export function PageTip({ id }: { id: TipId }) {
  const intl = useIntl()
  const userId = useMe().data?.user.id
  const seen = useSeen(userId, seenId.tip(id))
  const [tipsOn] = useTipsEnabled()
  const helpOpen = useHelpOpen()
  const [dismissed, setDismissed] = useState(false)
  const [granted, setGranted] = useState(false)
  const messageId = screenTips[id]
  const wanted = !!userId && !!messageId && !dismissed && tipsOn && !seen
  if (wanted && !granted && !helpOpen) setGranted(true)
  const showing = wanted && granted
  // Held from now, not from when the lazy bubble mounts, so no event tip opens in between.
  useEffect(() => {
    if (!showing) return
    setHelpOpen(true)
    return () => setHelpOpen(false)
  }, [showing])
  if (!userId || !messageId || !showing) return null

  return (
    <Suspense fallback={null}>
      <TipBubble
        message={intl.formatMessage({ id: messageId })}
        onDismiss={() => {
          markSeen(userId, seenId.tip(id))
          setDismissed(true)
        }}
      />
    </Suspense>
  )
}
