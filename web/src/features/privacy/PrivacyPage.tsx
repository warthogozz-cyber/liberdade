import { useState } from 'react'
import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { dangerButton, outlineButton } from './buttons'
import { ConsentList } from './ConsentList'
import { DeleteAccountSheet } from './DeleteAccountSheet'
import { DeleteFamilySheet } from './DeleteFamilySheet'
import { ExportButtons } from './ExportButtons'
import './register'

/** The person's data: download it, see the consents and the history, and ask for its deletion. */
export function PrivacyPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'privacy.title' })
  useDocumentTitle(title)
  const online = useOnline()
  const workspace = useCurrentWorkspace()
  const [deleting, setDeleting] = useState<'account' | 'family'>()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {!online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.offline' })}</p>
      )}
      <ExportButtons workspace={workspace} />
      <Link to="/mais/privacidade/atividade" className={outlineButton}>
        {intl.formatMessage({ id: 'privacy.activity' })}
      </Link>
      <h2 className="mt-2 text-lg font-semibold">
        {intl.formatMessage({ id: 'privacy.consents' })}
      </h2>
      <ConsentList />
      <div className="mt-4 flex flex-col gap-3">
        {workspace?.role === 'OWNER' && (
          <button
            type="button"
            disabled={!online}
            onClick={() => setDeleting('family')}
            className={dangerButton}
          >
            {intl.formatMessage({ id: 'privacy.deleteFamily' })}
          </button>
        )}
        <button
          type="button"
          disabled={!online}
          onClick={() => setDeleting('account')}
          className={dangerButton}
        >
          {intl.formatMessage({ id: 'privacy.deleteAccount' })}
        </button>
      </div>
      {deleting === 'account' && <DeleteAccountSheet onClose={() => setDeleting(undefined)} />}
      {deleting === 'family' && workspace && (
        <DeleteFamilySheet workspace={workspace} onClose={() => setDeleting(undefined)} />
      )}
    </main>
  )
}
