import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Navigate, useNavigate } from 'react-router'
import { useCreateWorkspace, useMe } from '@/shared/api'
import { firstName } from '@/shared/i18n'
import { saveQueryCacheNow, useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'

const MAX_NAME_LENGTH = 60

/** The first screen after sign-in: names the family and creates it. */
export function WelcomePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'welcome.pageTitle' }))
  const me = useMe().data
  const createWorkspace = useCreateWorkspace()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const online = useOnline()
  const [name, setName] = useState<string>()
  // The mutation's state reaches the button only on the next render; the ref stops a second tap
  // before that (Review Focus 1).
  const submitting = useRef(false)

  if (!me) return null
  // Only before this screen creates the family: once it does, it goes on to the new expense.
  if (me.workspaces.length > 0 && createWorkspace.isIdle) return <Navigate to="/inicio" replace />

  const value = name ?? me.suggestedWorkspaceName
  const trimmed = value.trim()

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (submitting.current || trimmed === '') return
    submitting.current = true
    try {
      await createWorkspace.mutateAsync(trimmed)
      // Saved before leaving, so a reload right away finds the family, not this page again.
      await saveQueryCacheNow(queryClient)
      await navigate('/lancar')
    } catch {
      // The mutation's error shows the message.
      submitting.current = false
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-6 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="welcome.greeting" values={{ name: firstName(me.user.displayName) }} />
      </h1>
      <form className="flex flex-col gap-6" onSubmit={(event) => void submit(event)}>
        <div className="flex flex-col gap-1">
          <label htmlFor="family-name" className="font-medium">
            <FormattedMessage id="welcome.familyName" />
          </label>
          <input
            id="family-name"
            className="min-h-12 rounded-field border border-text-muted bg-surface px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            value={value}
            maxLength={MAX_NAME_LENGTH}
            aria-describedby="family-name-hint"
            onChange={(event) => setName(event.target.value)}
          />
          <p id="family-name-hint" className="text-sm text-text-muted">
            <FormattedMessage id="welcome.changeLater" />
          </p>
        </div>
        <div>
          <p>
            <FormattedMessage id="welcome.weCreate" />
          </p>
          <ul className="list-disc pl-6 text-text-muted">
            <li>
              <FormattedMessage id="welcome.account" />
            </li>
            <li>
              <FormattedMessage id="welcome.categories" />
            </li>
          </ul>
        </div>
        {createWorkspace.isError && (
          <p role="alert" className="text-negative">
            <FormattedMessage id="welcome.error" />
          </p>
        )}
        <button
          type="submit"
          className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
          disabled={trimmed === '' || !online || createWorkspace.isPending}
        >
          <FormattedMessage id="welcome.start" />
        </button>
        {!online && (
          <p className="text-text-muted">
            <FormattedMessage id="welcome.offline" />
          </p>
        )}
      </form>
    </main>
  )
}
