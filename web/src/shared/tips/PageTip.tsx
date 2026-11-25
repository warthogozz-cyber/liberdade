import { lazy, Suspense, useState } from 'react'
import { useIntl } from 'react-intl'
import { useMe } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { tipsMessages } from './messages.pt-BR'
import { hasSeenTip, markTipSeen, type TipId } from './seenTips'
import { isTipsEnabled } from './tipsPreference'

registerMessages(tipsMessages)

// Kept out of the screen's chunk until a tip really shows.
const TipBubble = lazy(() => import('./TipBubble'))

const TIP_TEXT = {
  dashboard: 'tips.dashboard',
  transactions: 'tips.transactions',
  accounts: 'tips.accounts',
  calculator: 'tips.calculator',
  import: 'tips.import',
} as const

/** The first visit tip of a screen: shown once per person and device, unless tips are off. */
export function PageTip({ id }: { id: keyof typeof TIP_TEXT & TipId }) {
  const intl = useIntl()
  const userId = useMe().data?.user.id
  const [dismissed, setDismissed] = useState(false)
  if (!userId || dismissed || !isTipsEnabled() || hasSeenTip(userId, id)) return null

  return (
    <Suspense fallback={null}>
      <TipBubble
        message={intl.formatMessage({ id: TIP_TEXT[id] })}
        onDismiss={() => {
          markTipSeen(userId, id)
          setDismissed(true)
        }}
      />
    </Suspense>
  )
}
