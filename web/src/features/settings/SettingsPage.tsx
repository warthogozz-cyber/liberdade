import { useId, useState } from 'react'
import { useIntl } from 'react-intl'
import { playAchievement, unlockSound, useSoundEnabled } from '@/shared/sound'
import { useMe } from '@/shared/api'
import { resetAllSeen, setTipsEnabled, useTipsEnabled } from '@/shared/help'
import { useDocumentTitle } from '@/shared/ui'
import './register'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The settings of this device; today, the opt-in achievement sound. */
export function SettingsPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'settings.title' })
  useDocumentTitle(title)
  const [enabled, setEnabled] = useSoundEnabled()
  const [tipsOn, setTipsOn] = useTipsEnabled()
  const userId = useMe().data?.user.id
  const [reviewed, setReviewed] = useState(false)
  const helpId = useId()
  const tipsHelpId = useId()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <h2 className="text-lg font-semibold">{intl.formatMessage({ id: 'settings.sounds' })}</h2>
      <div className="flex items-center justify-between gap-4">
        <span className="font-medium">{intl.formatMessage({ id: 'settings.sound.label' })}</span>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          aria-label={intl.formatMessage({ id: 'settings.sound.label' })}
          aria-describedby={helpId}
          onClick={() => {
            if (!enabled) unlockSound()
            setEnabled(!enabled)
          }}
          className={`relative h-8 w-14 shrink-0 rounded-full border border-primary ${
            enabled ? 'bg-primary' : 'bg-surface'
          } ${focus}`}
        >
          <span
            aria-hidden="true"
            className={`absolute top-1 left-1 size-5 rounded-full bg-text ${
              enabled ? 'translate-x-6' : ''
            }`}
          />
        </button>
      </div>
      <p id={helpId} className="text-text-muted">
        {intl.formatMessage({ id: 'settings.sound.help' })}
      </p>
      <button
        type="button"
        onClick={() => {
          unlockSound()
          void playAchievement()
        }}
        className={`flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`}
      >
        {intl.formatMessage({ id: 'settings.sound.listen' })}
      </button>
      <h2 className="text-lg font-semibold">{intl.formatMessage({ id: 'settings.tips' })}</h2>
      <div className="flex items-center justify-between gap-4">
        <span className="font-medium">{intl.formatMessage({ id: 'settings.tips.label' })}</span>
        <button
          type="button"
          role="switch"
          aria-checked={tipsOn}
          aria-label={intl.formatMessage({ id: 'settings.tips.label' })}
          aria-describedby={tipsHelpId}
          onClick={() => setTipsOn(!tipsOn)}
          className={`relative h-8 w-14 shrink-0 rounded-full border border-primary ${
            tipsOn ? 'bg-primary' : 'bg-surface'
          } ${focus}`}
        >
          <span
            aria-hidden="true"
            className={`absolute top-1 left-1 size-5 rounded-full bg-text ${
              tipsOn ? 'translate-x-6' : ''
            }`}
          />
        </button>
      </div>
      <p id={tipsHelpId} className="text-text-muted">
        {intl.formatMessage({ id: 'settings.tips.help' })}
      </p>
      <button
        type="button"
        disabled={!userId}
        onClick={() => {
          if (!userId) return
          resetAllSeen(userId)
          setTipsEnabled(true)
          setReviewed(true)
        }}
        className={`flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-50 ${focus}`}
      >
        {intl.formatMessage({ id: 'settings.tips.review' })}
      </button>
      <p role="status" className="text-text-muted">
        {reviewed ? intl.formatMessage({ id: 'settings.tips.reviewed' }) : null}
      </p>
      <p className="text-text-muted">{intl.formatMessage({ id: 'settings.motion' })}</p>
    </main>
  )
}
