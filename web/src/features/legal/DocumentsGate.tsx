import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { LegalLink } from './LegalLink'
import { useDocuments } from './useDocuments'
import './labels.register'

/**
 * Shown in place of the family screens while the terms or the policy wait for an accept. It never
 * shows offline (see `useDocuments`), but the button still needs a connection.
 */
export function DocumentsGate() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'legal.gate.title' })
  useDocumentTitle(title)
  const online = useOnline()
  const documents = useDocuments()
  const [checked, setChecked] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<'legal.gate.outdated' | 'legal.gate.failed'>()

  async function accept() {
    setBusy(true)
    setError(undefined)
    try {
      await documents.accept()
    } catch (caught) {
      // On a 409 the list has been reloaded, so the gate stays with the versions in force.
      setError(
        caught instanceof ApiError && caught.status === 409
          ? 'legal.gate.outdated'
          : 'legal.gate.failed',
      )
      setChecked(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p>{intl.formatMessage({ id: 'legal.gate.intro' })}</p>
      <ul className="flex flex-col">
        <li className="min-h-12 py-3">
          <LegalLink to="/termos">{intl.formatMessage({ id: 'legal.terms' })}</LegalLink>
        </li>
        <li className="min-h-12 py-3">
          <LegalLink to="/privacidade">{intl.formatMessage({ id: 'legal.privacy' })}</LegalLink>
        </li>
      </ul>
      <label className="flex min-h-12 items-center gap-3">
        <input
          type="checkbox"
          checked={checked}
          onChange={(event) => setChecked(event.target.checked)}
          className="size-6 shrink-0"
        />
        <span>{intl.formatMessage({ id: 'legal.gate.checkbox' })}</span>
      </label>
      {error && (
        <p role="alert" className="text-negative">
          {intl.formatMessage({ id: error })}
        </p>
      )}
      <button
        type="button"
        disabled={!checked || !online || busy}
        onClick={() => void accept()}
        className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        {intl.formatMessage({ id: 'legal.gate.continue' })}
      </button>
      {!online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'legal.gate.offline' })}</p>
      )}
    </main>
  )
}
