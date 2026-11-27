import { lazy, Suspense, useState } from 'react'
import { useIntl } from 'react-intl'
import { useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { screenTips, seenId, type TipId } from './catalog'
import { markSeen, useSeen, useTipsEnabled } from './helpStore'
import { helpMessages } from './messages.pt-BR'

registerMessages(helpMessages)

// Kept out of the screen's chunk until a tip really shows.
const TipBubble = lazy(() => import('./TipBubble'))

/** The first visit tip of a screen: shown once per person and device, unless tips are off. */
export function PageTip({ id }: { id: TipId }) {
  const intl = useIntl()
  const userId = useMe().data?.user.id
  const seen = useSeen(userId, seenId.tip(id))
  const [tipsOn] = useTipsEnabled()
  const [dismissed, setDismissed] = useState(false)
  const messageId = screenTips[id]
  if (!userId || !messageId || dismissed || !tipsOn || seen) return null

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
