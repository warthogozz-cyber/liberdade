import { useEffect } from 'react'
import { useIntl } from 'react-intl'
import { setHelpOpen } from './helpStore'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The tip as a block at the top of the screen, inside the flow so it covers no control. */
export default function TipBubble({
  message,
  onDismiss,
}: {
  message: string
  onDismiss: () => void
}) {
  const intl = useIntl()
  useEffect(() => {
    setHelpOpen(true)
    return () => setHelpOpen(false)
  }, [])
  return (
    <div
      role="note"
      className="flex items-center justify-between gap-3 rounded-card bg-surface p-4 text-text"
    >
      <p>{message}</p>
      <button
        type="button"
        onClick={onDismiss}
        className={`min-h-12 shrink-0 rounded-field border border-primary px-4 font-medium text-primary ${focus}`}
      >
        {intl.formatMessage({ id: 'tips.dismiss' })}
      </button>
    </div>
  )
}
