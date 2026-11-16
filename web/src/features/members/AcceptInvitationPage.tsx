import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import { Link, useNavigate } from 'react-router'
import {
  ApiError,
  getMe,
  queryKeys,
  selectWorkspace,
  useAcceptInvitation,
  usePreviewInvitation,
  type InvitationPreview,
} from '@/shared/api'
import { startGoogleSignIn } from '@/shared/auth'
import { registerPasskey } from '@/shared/auth/passkey'
import { broadcastSignedIn, clearSession, useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { clearInvitationToken, readInvitationToken } from './invitationToken'
import './register'

type Failure = 'gone' | 'notFound' | 'failed'

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
  const [failure, setFailure] = useState<Failure | undefined>(token ? undefined : 'notFound')
  const [displayName, setDisplayName] = useState('')
  const [step, setStep] = useState<'link' | 'passkey'>('link')
  const [passkeyFailed, setPasskeyFailed] = useState(false)
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  // The state reaches the buttons only on the next render; the ref stops a second tap before that.
  const working = useRef(false)
  const started = useRef(false)

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
      if (caught instanceof ApiError && caught.status === 410) setFailure('gone')
      else if (caught instanceof ApiError && caught.status === 404) setFailure('notFound')
      else setFailure('failed')
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

  /** Accepts, then starts the new session's data: what the cache held belonged to someone else. */
  async function accept(newUser?: { displayName: string }, freshSession = false) {
    if (!token) return
    const accepted = await acceptInvitation.mutateAsync({ token, newUser })
    if (freshSession) await clearSession(queryClient)
    const me = await queryClient.fetchQuery({
      queryKey: queryKeys.me,
      queryFn: getMe,
      staleTime: 0,
    })
    if (freshSession) broadcastSignedIn(me.user.id)
    selectWorkspace(accepted.workspaceId)
    clearInvitationToken()
    if (accepted.mustRegisterPasskey) {
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
      // The token stays in this tab's storage, so the page can read it again on the way back.
      await clearSession(queryClient)
      startGoogleSignIn({ returnTo: '/convite' })
    })
  }

  async function createPasskey() {
    await guarded(async () => {
      setPasskeyFailed(false)
      try {
        await registerPasskey(
          preview?.nickname ?? intl.formatMessage({ id: 'passkey.defaultLabel' }),
        )
      } catch {
        setPasskeyFailed(true)
        return
      }
      await queryClient.invalidateQueries({ queryKey: queryKeys.me })
      void navigate('/inicio', { replace: true })
    })
  }

  function retry() {
    if (token) void open(token)
  }

  let content
  if (step === 'passkey') {
    content = (
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{intl.formatMessage({ id: 'accept.passkeyTitle' })}</h1>
        <p>{intl.formatMessage({ id: 'accept.passkeyRequired' })}</p>
        {passkeyFailed && (
          <p role="alert" className="text-negative">
            {intl.formatMessage({ id: 'accept.passkeyFailed' })}
          </p>
        )}
        <button type="button" disabled={busy || !online} onClick={() => void createPasskey()} className={primary}>
          {intl.formatMessage({ id: passkeyFailed ? 'accept.passkeyRetry' : 'accept.passkeyCreate' })}
        </button>
      </section>
    )
  } else if (failure) {
    content = (
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{intl.formatMessage({ id: 'accept.title' })}</h1>
        <p role="alert">{intl.formatMessage({ id: `accept.${failure}` })}</p>
        {failure === 'failed' && token ? (
          <button type="button" onClick={retry} className={primary}>
            {intl.formatMessage({ id: 'accept.passkeyRetry' })}
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
            <button type="button" disabled={busy || !online} onClick={() => void recover()} className={primary}>
              {intl.formatMessage({ id: 'accept.recoverAction' })}
            </button>
          </>
        ) : signedIn ? (
          <button type="button" disabled={busy || !online} onClick={() => void join()} className={primary}>
            {intl.formatMessage({ id: 'accept.join' })}
          </button>
        ) : (
          <>
            {preview.role === 'YOUTH' ? (
              <p className="text-text-muted">{intl.formatMessage({ id: 'accept.youthPasskeyOnly' })}</p>
            ) : (
              <button type="button" disabled={busy || !online} onClick={() => void withGoogle()} className={outline}>
                {intl.formatMessage({ id: 'accept.google' })}
              </button>
            )}
            <form onSubmit={(event) => void createWithPasskey(event)} className="flex flex-col gap-3">
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
