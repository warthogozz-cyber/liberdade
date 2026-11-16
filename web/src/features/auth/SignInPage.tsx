import { browserSupportsWebAuthn } from '@simplewebauthn/browser'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/shared/api'
import { startGoogleSignIn } from '@/shared/auth'
import { signInWithPasskey } from '@/shared/auth/passkey'
import { clearSession, useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import type { signInMessages } from './signIn.messages.pt-BR'
import './signIn.register'

type MessageId = keyof typeof signInMessages

/** The messages the API's redirect can ask for with `?erro=` (Review Focus 3). */
const URL_ERRORS: Record<string, MessageId> = {
  login: 'signIn.error.login',
  limite: 'signIn.error.rateLimited',
}

const button =
  'min-h-12 w-full rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/**
 * The person closed or refused the device's passkey prompt. Checked by name: the browser's
 * `DOMException` is not an `Error` in every environment.
 */
function isCancelled(error: unknown): boolean {
  return (error as { name?: unknown } | null)?.name === 'NotAllowedError'
}

export function SignInPage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'signIn.pageTitle' }))
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const online = useOnline()
  const [busy, setBusy] = useState(false)
  // The state reaches the button only on the next render; the ref stops a second tap before that.
  const busyRef = useRef(false)
  const [error, setError] = useState<MessageId>()
  const shownError = error ?? URL_ERRORS[params.get('erro') ?? '']
  const supportsPasskey = browserSupportsWebAuthn()

  function claim(): boolean {
    if (busyRef.current) return false
    busyRef.current = true
    setBusy(true)
    setError(undefined)
    return true
  }

  function release(): void {
    busyRef.current = false
    setBusy(false)
  }

  // Back from Google can restore this page from the bfcache, still busy from the redirect.
  useEffect(() => {
    function onShow(event: PageTransitionEvent) {
      if (!event.persisted) return
      busyRef.current = false
      setBusy(false)
    }
    window.addEventListener('pageshow', onShow)
    return () => window.removeEventListener('pageshow', onShow)
  }, [])

  async function signInWithGoogle() {
    if (!claim()) return
    try {
      // The new session starts with an empty cache (Review Focus 2).
      await clearSession(queryClient)
      startGoogleSignIn()
    } catch {
      setError('signIn.error.login')
      release()
    }
  }

  async function passkeySignIn() {
    if (!claim()) return
    try {
      await signInWithPasskey()
      // The new session starts with an empty cache and fetches a fresh CSRF token (T10-csrf).
      await clearSession(queryClient)
      await navigate('/')
      return
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 429) setError('signIn.error.rateLimited')
      else if (!isCancelled(caught)) setError('signIn.error.passkey')
    }
    release()
  }

  const disabled = busy || !online

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-6 p-6 text-center">
      <div className="flex flex-col gap-2">
        <h1 className="text-[32px] font-semibold">
          <FormattedMessage id="app.name" />
        </h1>
        <p className="text-text-muted">
          <FormattedMessage id="signIn.tagline" />
        </p>
      </div>
      {shownError && (
        <p role="alert" className="text-negative">
          <FormattedMessage id={shownError} />
        </p>
      )}
      <div className="flex flex-col gap-3">
        <button
          type="button"
          className={`${button} bg-primary text-on-primary`}
          disabled={disabled}
          onClick={() => void signInWithGoogle()}
        >
          <FormattedMessage id="signIn.google" />
        </button>
        {supportsPasskey && (
          <button
            type="button"
            className={`${button} border border-primary text-primary`}
            disabled={disabled}
            onClick={() => void passkeySignIn()}
          >
            <FormattedMessage id="signIn.passkey" />
          </button>
        )}
        {!online && (
          <p className="text-text-muted">
            <FormattedMessage id="signIn.offline" />
          </p>
        )}
      </div>
      <p className="text-sm text-text-muted">
        <FormattedMessage id="signIn.noPassword" />
      </p>
    </main>
  )
}
