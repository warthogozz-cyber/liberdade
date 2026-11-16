import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, ReauthCancelledError } from '@/shared/api'
import { useConsents, useRevokeConsent, type ConsentView } from '@/shared/api/privacy'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { ConfirmDialog, useToast } from '@/shared/ui'
import { outlineButton } from './buttons'
import './register'

const PURPOSE = {
  PARENTAL: 'privacy.consent.parental',
  TERMS: 'privacy.consent.terms',
  PRIVACY: 'privacy.consent.privacy',
} as const

/** The consents the person gave or received, and the way to take one back. */
export function ConsentList() {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const run = useReauth()
  const consents = useConsents()
  const revoke = useRevokeConsent()
  const [asking, setAsking] = useState<ConsentView>()
  const [error, setError] = useState<string>()

  if (consents.isPending) return <p>{intl.formatMessage({ id: 'common.loading' })}</p>
  if (consents.isError) {
    return <p role="alert">{intl.formatMessage({ id: 'privacy.consents.loadFailed' })}</p>
  }
  if (consents.data.length === 0) {
    return <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.consents.empty' })}</p>
  }

  async function confirm(consent: ConsentView) {
    setAsking(undefined)
    setError(undefined)
    try {
      await run(() => revoke.mutateAsync(consent.id))
    } catch (failure) {
      if (failure instanceof ReauthCancelledError) return
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'privacy.consent.revokedToast' }) })
  }

  const date = (value: string) => intl.formatDate(value, { dateStyle: 'short' })

  return (
    <>
      <ul className="flex flex-col divide-y divide-border">
        {consents.data.map((consent) => (
          <li key={consent.id} className="flex flex-col gap-2 py-3">
            <div>
              <p className="font-medium">{intl.formatMessage({ id: PURPOSE[consent.purpose] })}</p>
              <p className="text-sm text-text-muted">
                {intl.formatMessage(
                  { id: 'privacy.consent.detail' },
                  {
                    subject:
                      consent.subjectName ?? intl.formatMessage({ id: 'activity.formerMember' }),
                    family: consent.workspaceName,
                    date: date(consent.grantedAt),
                  },
                )}
              </p>
              {consent.grantedByName && (
                <p className="text-sm text-text-muted">
                  {intl.formatMessage(
                    { id: 'privacy.consent.givenBy' },
                    { name: consent.grantedByName },
                  )}
                </p>
              )}
              {consent.revokedAt && (
                <p className="text-sm text-text-muted">
                  {intl.formatMessage(
                    { id: 'privacy.consent.revoked' },
                    { date: date(consent.revokedAt) },
                  )}
                </p>
              )}
              {!consent.revokedAt && consent.supersededAt && (
                <p className="text-sm text-text-muted">
                  {intl.formatMessage(
                    { id: 'privacy.consent.superseded' },
                    { date: date(consent.supersededAt) },
                  )}
                </p>
              )}
            </div>
            {consent.canRevoke && (
              <button
                type="button"
                disabled={!online || revoke.isPending}
                onClick={() => setAsking(consent)}
                className={outlineButton}
              >
                {intl.formatMessage({ id: 'privacy.consent.revoke' })}
              </button>
            )}
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      <ConfirmDialog
        open={asking !== undefined}
        title={intl.formatMessage({ id: 'privacy.consent.revokeTitle' })}
        body={intl.formatMessage({ id: 'privacy.consent.revokeBody' })}
        confirmLabel={intl.formatMessage({ id: 'privacy.consent.revokeConfirm' })}
        cancelLabel={intl.formatMessage({ id: 'privacy.cancel' })}
        onConfirm={() => asking && void confirm(asking)}
        onCancel={() => setAsking(undefined)}
      />
    </>
  )
}
