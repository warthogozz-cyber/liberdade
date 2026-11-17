import { useState } from 'react'
import { useIntl } from 'react-intl'
import { usePasskeys, type PasskeyView } from '@/shared/api/privacy'
import { useOnline } from '@/shared/offline'
import { outlineButton } from './buttons'
import { RemovePasskeySheet } from './RemovePasskeySheet'
import './register'

/** The passkeys the person signs in with, and the way to remove one. */
export function PasskeyList() {
  const intl = useIntl()
  const online = useOnline()
  const passkeys = usePasskeys()
  const [removing, setRemoving] = useState<PasskeyView>()

  if (passkeys.isPending) return <p>{intl.formatMessage({ id: 'common.loading' })}</p>
  if (passkeys.isError) {
    return <p role="alert">{intl.formatMessage({ id: 'privacy.passkeys.loadFailed' })}</p>
  }
  if (passkeys.data.length === 0) {
    return <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.passkeys.empty' })}</p>
  }

  const date = (value: string) => intl.formatDate(value, { dateStyle: 'short' })

  return (
    <>
      <ul className="flex flex-col divide-y divide-border">
        {passkeys.data.map((passkey) => (
          <li key={passkey.id} className="flex items-center justify-between gap-3 py-3">
            <div>
              <p className="font-medium">{passkey.label}</p>
              <p className="text-sm text-text-muted">
                {intl.formatMessage(
                  { id: 'privacy.passkey.created' },
                  { date: date(passkey.createdAt) },
                )}
              </p>
              <p className="text-sm text-text-muted">
                {passkey.lastUsedAt
                  ? intl.formatMessage(
                      { id: 'privacy.passkey.lastUsed' },
                      { date: date(passkey.lastUsedAt) },
                    )
                  : intl.formatMessage({ id: 'privacy.passkey.neverUsed' })}
              </p>
            </div>
            <button
              type="button"
              disabled={!online}
              aria-label={intl.formatMessage(
                { id: 'privacy.passkey.removeNamed' },
                { name: passkey.label },
              )}
              onClick={() => setRemoving(passkey)}
              className={`${outlineButton} min-h-11`}
            >
              {intl.formatMessage({ id: 'privacy.passkey.remove' })}
            </button>
          </li>
        ))}
      </ul>
      {removing && <RemovePasskeySheet passkey={removing} onClose={() => setRemoving(undefined)} />}
    </>
  )
}
