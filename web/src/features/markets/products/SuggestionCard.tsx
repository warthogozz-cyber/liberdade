import { FormattedMessage } from 'react-intl'
import type { SuggestedPair } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from '../messages.pt-BR'

registerMessages(marketsMessages)

interface SuggestionCardProps {
  pair: SuggestedPair
  busy: boolean
  onMerge: () => void
  onDismiss: () => void
}

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button = `min-h-12 flex-1 rounded-field px-4 font-medium disabled:opacity-50 ${focus}`

/** Two products that may be one: the person joins them or says they are not. Plain words, no score. */
export function SuggestionCard({ pair, busy, onMerge, onDismiss }: SuggestionCardProps) {
  return (
    <li className="flex flex-col gap-3 rounded-card bg-surface px-4 py-3">
      <p className="text-sm text-text-muted">
        <FormattedMessage id="markets.products.suggestion.title" />
      </p>
      <p className="flex flex-col font-medium">
        <span>{pair.a.name}</span>
        <span>{pair.b.name}</span>
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={busy}
          onClick={onMerge}
          className={`${button} bg-primary text-on-primary`}
        >
          <FormattedMessage id="markets.products.suggestion.merge" />
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={onDismiss}
          className={`${button} border border-primary text-primary`}
        >
          <FormattedMessage id="markets.products.suggestion.dismiss" />
        </button>
      </div>
    </li>
  )
}
