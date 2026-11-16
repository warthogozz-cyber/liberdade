import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { flushSync } from 'react-dom'
import { useIntl } from 'react-intl'
import { Link, useNavigate } from 'react-router'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { ApiError, getMe, queryKeys, selectWorkspace, type InvitationPreview } from '@/shared/api'
import { useAcceptInvitation, usePreviewInvitation } from '@/shared/api/family'
import { startGoogleSignIn } from '@/shared/auth'
import { registerPasskey } from '@/shared/auth/passkey'
import { broadcastSignedIn, clearSession, saveQueryCacheNow, useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import {
  clearInvitationToken,
  clearPendingPasskey,
  readInvitationToken,
  readPendingPasskey,
  rememberInvitationToken,
  storePendingPasskey,
} from './invitationToken'
import './register'

type Failure = 'gone' | 'notFound' | 'failed'

/** What the page needs once the API has taken the person in, to finish without asking again. */
interface Joined {
  workspaceId: string
  mustRegisterPasskey: boolean
  freshSession: boolean
}

/** The API's answer when a signed-in person with Google takes a Jovem link. */
function isYouthNeedsPasskey(caught: unknown): boolean {
  return (
    caught instanceof ApiError &&
    caught.status === 422 &&
    caught.code === 'invitation.youth-needs-passkey'
  )
}

function failureOf(caught: unknown): Failure {
  if (caught instanceof ApiError && caught.status === 410) return 'gone'
  if (caught instanceof ApiError && caught.status === 404) return 'notFound'
  return 'failed'
}

const button =
  'flex min-h-12 w-full items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const primary = `${button} bg-primary text-on-primary`
const outline = `${button} border border-primary text-primary`

/**
 * Opens an invitation or recovery link. It sits outside the signed-in layout: the person arriving
 * usually has no session yet, and a 401 here must not send them to the sign-in page.
 */
export function AcceptInvitationPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'accept.title' }))
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const online = useOnline()
  const previewInvitation = usePreviewInvitation()
  const acceptInvitation = useAcceptInvitation()

  const [token] = useState(readInvitationToken)
  const [preview, setPreview] = useState<InvitationPreview>()
  const [signedIn, setSignedIn] = useState(false)
  // The API turned the Jovem link down for the account signed in, though the page had not seen it.
  const [youthSignOut, setYouthSignOut] = useState(false)
  const [failure, setFailure] = useState<Failure | undefined>(token ? undefined : 'notFound')
  const [displayName, setDisplayName] = useState('')
  // A reload after joining has no token left, only the passkey still to be made.
  const [step, setStep] = useState<'link' | 'passkey'>(() =>
    !token && readPendingPasskey() !== undefined ? 'passkey' : 'link',
  )
  const [retryOpen, setRetryOpen] = useState(false)
  const [finishFailed, setFinishFailed] = useState(false)
  const [passkeyFailed, setPasskeyFailed] = useState(false)
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  // The state reaches the buttons only on the next render; the ref stops a second tap before that.
  const working = useRef(false)
  const started = useRef(false)
  const joined = useRef<Joined | undefined>(undefined)

  async function open(value: string) {
    setFailure(undefined)
    try {
      // A direct call, not a query: a 401 means "no session yet" here, not "go sign in".
      const [me, opened] = await Promise.all([
        getMe().catch(() => undefined),
        previewInvitation.mutateAsync(value),
      ])
      if (me) {
        queryClient.setQueryData(queryKeys.me, me)
        setSignedIn(true)
      }
      setPreview(opened)
    } catch (caught) {
      clearInvitationToken()
      const failed = failureOf(caught)
      setFailure(failed)
      // Only reading the link failed, so reading it again is worth a try.
      setRetryOpen(failed === 'failed')
    }
  }

  useEffect(() => {
    if (!token) {
      clearInvitationToken()
      return
    }
    if (started.current) return
    started.current = true
    void open(token)
    // `open` only reads state set before; the link is opened once per visit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  async function guarded(task: () => Promise<void>) {
    if (working.current) return
    working.current = true
    setBusy(true)
    setError(undefined)
    try {
      await task()
    } catch (caught) {
      setError(
        (caught instanceof ApiError && caught.detail) ||
          intl.formatMessage({ id: 'accept.failed' }),
      )
    } finally {
      working.current = false
      setBusy(false)
    }
  }

  function passkeyLabel(): string {
    return (
      readPendingPasskey() ??
      preview?.nickname ??
      intl.formatMessage({ id: 'passkey.defaultLabel' })
    )
  }

  /**
   * Accepts. Once the API answers, the link is spent: the token goes and the passkey step is
   * remembered, so nothing after this can send the person back to a link that no longer works.
   */
  async function accept(newUser?: { displayName: string }, freshSession = false) {
    if (!token) return
    let accepted
    try {
      accepted = await acceptInvitation.mutateAsync({ token, newUser })
    } catch (caught) {
      if (isYouthNeedsPasskey(caught)) {
        // The link is still good, only not for this account: it waits for the person to sign out.
        setYouthSignOut(true)
        return
      }
      // Whatever went wrong, this link is no use again: it was used, it is gone, or it never was.
      clearInvitationToken()
      setFailure(failureOf(caught))
      setRetryOpen(false)
      return
    }
    clearInvitationToken()
    if (freshSession) {
      try {
        // What the cache held belonged to someone else; the refetch below fills it again.
        await clearSession(queryClient)
      } catch {
        // Best effort: the session's data is replaced right after.
      }
    }
    if (accepted.mustRegisterPasskey) storePendingPasskey(passkeyLabel())
    joined.current = {
      workspaceId: accepted.workspaceId,
      mustRegisterPasskey: accepted.mustRegisterPasskey,
      freshSession,
    }
    await finish()
  }

  /** Starts the new session's data. Never accepts again: a failure here is retried from here. */
  async function finish() {
    const done = joined.current
    if (!done) return
    let me
    try {
      me = await queryClient.fetchQuery({ queryKey: queryKeys.me, queryFn: getMe, staleTime: 0 })
    } catch {
      setFinishFailed(true)
      return
    }
    setFinishFailed(false)
    if (done.freshSession) broadcastSignedIn(me.user.id)
    selectWorkspace(done.workspaceId)
    // Saved past the throttle, so a reload right away finds the family, not the welcome page.
    await saveQueryCacheNow(queryClient)
    if (done.mustRegisterPasskey) {
      setStep('passkey')
      return
    }
    void navigate('/inicio', { replace: true })
  }

  async function join() {
    await guarded(() => accept(undefined, false))
  }

  async function recover() {
    await guarded(() => accept(undefined, true))
  }

  async function createWithPasskey(event: FormEvent) {
    event.preventDefault()
    const name = displayName.trim()
    if (name === '') return
    await guarded(() => accept({ displayName: name }, true))
  }

  async function withGoogle() {
    await guarded(async () => {
      await clearSession(queryClient)
      // Kept in this tab's storage again, so the page can read it on the way back.
      if (token) rememberInvitationToken(token)
      startGoogleSignIn({ returnTo: '/convite' })
    })
  }

  async function createPasskey() {
    await guarded(async () => {
      setPasskeyFailed(false)
      try {
        await registerPasskey(passkeyLabel())
      } catch {
        setPasskeyFailed(true)
        return
      }
      clearPendingPasskey()
      await queryClient.invalidateQueries({ queryKey: queryKeys.me })
      void navigate('/inicio', { replace: true })
    })
  }

  /** Leaves the signed-in view at once, so nothing on screen reads the session while it goes. */
  function leaveSession() {
    flushSync(() => {
      setSignedIn(false)
      setYouthSignOut(false)
    })
  }

  /** The same link carries on once the session is gone: the token stays in this tab. */
  function signedOut() {
    if (token) rememberInvitationToken(token)
  }

  function retry() {
    if (token) void open(token)
  }

  let content
  if (step === 'passkey') {
    content = (
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">
          {intl.formatMessage({ id: 'accept.passkeyTitle' })}
        </h1>
        <p>{intl.formatMessage({ id: 'accept.passkeyRequired' })}</p>
        {passkeyFailed && (
          <p role="alert" className="text-negative">
            {intl.formatMessage({ id: 'accept.passkeyFailed' })}
          </p>
        )}
        <button
          type="button"
          disabled={busy || !online}
          onClick={() => void createPasskey()}
          className={primary}
        >
          {intl.formatMessage({
            id: passkeyFailed ? 'accept.passkeyRetry' : 'accept.passkeyCreate',
          })}
        </button>
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'accept.passkeyOffline' })}</p>
        )}
      </section>
    )
  } else if (finishFailed) {
    content = (
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{intl.formatMessage({ id: 'accept.title' })}</h1>
        <p role="alert">{intl.formatMessage({ id: 'accept.failed' })}</p>
        <button
          type="button"
          disabled={busy}
          onClick={() => void guarded(finish)}
          className={primary}
        >
          {intl.formatMessage({ id: 'accept.retry' })}
        </button>
      </section>
    )
  } else if (failure) {
    content = (
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{intl.formatMessage({ id: 'accept.title' })}</h1>
        <p role="alert">{intl.formatMessage({ id: `accept.${failure}` })}</p>
        {failure === 'failed' && retryOpen && token ? (
          <button type="button" onClick={retry} className={primary}>
            {intl.formatMessage({ id: 'accept.retry' })}
          </button>
        ) : (
          <Link to="/" className={outline}>
            {intl.formatMessage({ id: 'accept.home' })}
          </Link>
        )}
      </section>
    )
  } else if (!preview) {
    content = <p>{intl.formatMessage({ id: 'accept.loading' })}</p>
  } else {
    content = (
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">
          {preview.kind === 'RECOVERY'
            ? preview.nickname
              ? intl.formatMessage({ id: 'accept.recovery' }, { name: preview.nickname })
              : intl.formatMessage(
                  { id: 'accept.recoveryAnonymous' },
                  { family: preview.workspaceName },
                )
            : preview.invitedBy
              ? intl.formatMessage(
                  { id: 'accept.invited' },
                  {
                    inviter: preview.invitedBy,
                    family: preview.workspaceName,
                    role: intl.formatMessage({ id: `members.role.${preview.role}` }),
                  },
                )
              : intl.formatMessage(
                  { id: 'accept.invitedAnonymous' },
                  {
                    family: preview.workspaceName,
                    role: intl.formatMessage({ id: `members.role.${preview.role}` }),
                  },
                )}
        </h1>

        {preview.kind === 'RECOVERY' ? (
          <>
            <p className="text-text-muted">{intl.formatMessage({ id: 'accept.recoveryHint' })}</p>
            <button
              type="button"
              disabled={busy || !online}
              onClick={() => void recover()}
              className={primary}
            >
              {intl.formatMessage({ id: 'accept.recoverAction' })}
            </button>
          </>
        ) : youthSignOut || (signedIn && preview.role === 'YOUTH') ? (
          <>
            <p>{intl.formatMessage({ id: 'accept.youthSignOut' })}</p>
            <SignOutButton leave={leaveSession} onSignedOut={signedOut} />
          </>
        ) : signedIn ? (
          <button
            type="button"
            disabled={busy || !online}
            onClick={() => void join()}
            className={primary}
          >
            {intl.formatMessage({ id: 'accept.join' })}
          </button>
        ) : (
          <>
            {preview.role === 'YOUTH' ? (
              <p className="text-text-muted">
                {intl.formatMessage({ id: 'accept.youthPasskeyOnly' })}
              </p>
            ) : (
              <button
                type="button"
                disabled={busy || !online}
                onClick={() => void withGoogle()}
                className={outline}
              >
                {intl.formatMessage({ id: 'accept.google' })}
              </button>
            )}
            <form
              onSubmit={(event) => void createWithPasskey(event)}
              className="flex flex-col gap-3"
            >
              <label className="flex flex-col gap-1">
                <span className="font-medium">{intl.formatMessage({ id: 'accept.name' })}</span>
                <input
                  value={displayName}
                  maxLength={60}
                  autoComplete="name"
                  onChange={(event) => setDisplayName(event.target.value)}
                  className="min-h-12 rounded-field border border-border bg-bg px-3"
                />
              </label>
              <button
                type="submit"
                disabled={busy || !online || displayName.trim() === ''}
                className={primary}
              >
                {intl.formatMessage({ id: 'accept.passkey' })}
              </button>
            </form>
          </>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'accept.offline' })}</p>
        )}
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
      </section>
    )
  }

  return <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">{content}</main>
}
