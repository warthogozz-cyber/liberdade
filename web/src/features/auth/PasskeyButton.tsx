import {
  startRegistration,
  type PublicKeyCredentialCreationOptionsJSON,
} from '@simplewebauthn/browser'
import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { postJson, queryKeys, useCurrentWorkspace, useMe } from '@/shared/api'
import { DEFAULT_TIMEZONE } from '@/shared/i18n'
import { useOnline } from '@/shared/offline'

type Result = 'created' | 'failed'

/** Creates a passkey on this device for the signed-in person, and tells when the last one was made. */
export function PasskeyButton() {
  const intl = useIntl()
  const queryClient = useQueryClient()
  const online = useOnline()
  const createdAt = useMe().data?.user.passkeyCreatedAt
  const timeZone = useCurrentWorkspace()?.timezone ?? DEFAULT_TIMEZONE
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [result, setResult] = useState<Result>()

  async function create() {
    if (busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setResult(undefined)
    try {
      const optionsJSON = (await postJson(
        '/webauthn/register/options',
      )) as PublicKeyCredentialCreationOptionsJSON
      const credential = await startRegistration({ optionsJSON })
      const label = intl.formatMessage({ id: 'passkey.defaultLabel' })
      await postJson('/webauthn/register', { publicKey: { credential, label } })
      setResult('created')
      void queryClient.invalidateQueries({ queryKey: queryKeys.me })
    } catch {
      setResult('failed')
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        className="min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
        disabled={busy || !online}
        onClick={() => void create()}
      >
        <FormattedMessage id="passkey.create" />
      </button>
      {!online && (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="passkey.offline" />
        </p>
      )}
      {createdAt && (
        <p className="text-sm text-text-muted">
          <FormattedMessage
            id="passkey.createdOn"
            values={{ date: intl.formatDate(createdAt, { dateStyle: 'long', timeZone }) }}
          />
        </p>
      )}
      <p role="status" className="text-sm">
        {result === 'created' && <FormattedMessage id="passkey.created" />}
      </p>
      {result === 'failed' && (
        <p role="alert" className="text-sm text-negative">
          <FormattedMessage id="passkey.failed" />
        </p>
      )}
    </div>
  )
}
