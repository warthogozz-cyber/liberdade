import { useState } from 'react'
import { useIntl } from 'react-intl'
import { LegalLink } from './LegalLink'
import { useDocuments } from './useDocuments'
import './labels.register'

/** Where the dismissal of the notice of one policy version is kept. */
const policyNoticeKey = (version: string) => `liberdade.policyNotice.${version}`

function wasDismissed(version: string): boolean {
  try {
    return localStorage.getItem(policyNoticeKey(version)) !== null
  } catch {
    return false
  }
}

/**
 * Tells the person the privacy policy changed, when what they accepted still counts and so nothing
 * is asked of them. It closes for good per version; without storage it closes for the session only.
 */
export function PolicyUpdatedNotice() {
  const intl = useIntl()
  const { policyUpdated } = useDocuments()
  // The versions closed in this session, so the notice also goes away where storage fails.
  const [closed, setClosed] = useState<string[]>([])
  if (!policyUpdated || closed.includes(policyUpdated) || wasDismissed(policyUpdated)) return null

  function dismiss(version: string) {
    try {
      localStorage.setItem(policyNoticeKey(version), '1')
    } catch {
      // Without storage the notice shows again next visit, which is the safe side.
    }
    setClosed((versions) => [...versions, version])
  }

  return (
    <section
      role="status"
      className="mx-auto flex max-w-lg flex-col gap-2 rounded-card bg-surface p-4"
    >
      <p>{intl.formatMessage({ id: 'legal.policyUpdated.text' })}</p>
      <div className="flex flex-wrap items-center gap-4">
        <span className="inline-flex min-h-12 items-center">
          <LegalLink to="/privacidade">
            {intl.formatMessage({ id: 'legal.policyUpdated.read' })}
          </LegalLink>
        </span>
        <button
          type="button"
          onClick={() => dismiss(policyUpdated)}
          className="min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {intl.formatMessage({ id: 'legal.policyUpdated.dismiss' })}
        </button>
      </div>
    </section>
  )
}
