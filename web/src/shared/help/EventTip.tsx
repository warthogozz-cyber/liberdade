import { useEffect } from 'react'
import { useIntl } from 'react-intl'
import { setHelpOpen } from './helpStore'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The tip of an event, as a status message at the bottom of the screen; it covers no control above. */
export default function EventTip({
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
      role="status"
      className="fixed inset-x-4 bottom-20 z-40 mx-auto flex max-w-lg items-center justify-between gap-3 rounded-card border border-primary bg-surface p-4 text-text shadow-lg"
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
