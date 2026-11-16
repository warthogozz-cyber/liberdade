import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, Navigate } from 'react-router'
import {
  ApiError,
  ReauthCancelledError,
  useCreateInvitation,
  useCurrentWorkspace,
  type MeWorkspace,
  type MemberRole,
} from '@/shared/api'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { InvitationLink } from './InvitationLink'
import { canInvite, invitableRoles } from './permissions'
import './register'

const field = 'min-h-12 rounded-field border border-border bg-bg px-3'
const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** Makes an invitation link for someone who is not in the family yet. */
export function InvitePage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'invite.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()

  // Youth and Viewer cannot invite; the family page is where they belong.
  if (workspace && !canInvite(workspace.role)) return <Navigate to="/mais/familia" replace />

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {workspace ? (
        <InviteForm workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function InviteForm({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const online = useOnline()
  const run = useReauth()
  const create = useCreateInvitation(workspace.id)
  const roles = invitableRoles(workspace.role)
  const [nickname, setNickname] = useState('')
  const [role, setRole] = useState<MemberRole>('ADULT')
  const [consent, setConsent] = useState(false)
  const [error, setError] = useState<string>()
  const [created, setCreated] = useState<{ token: string; nickname: string }>()

  const isYouth = role === 'YOUTH'
  const canCreate =
    online && !create.isPending && nickname.trim() !== '' && (!isYouth || consent)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!canCreate) return
    setError(undefined)
    const trimmed = nickname.trim()
    try {
      const invitation = await run(() =>
        create.mutateAsync({
          nickname: trimmed,
          role,
          ...(isYouth ? { parentalConsent: true } : {}),
        }),
      )
      if (invitation.token) setCreated({ token: invitation.token, nickname: trimmed })
    } catch (failure) {
      if (failure instanceof ReauthCancelledError) return
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
    }
  }

  function another() {
    setCreated(undefined)
    setNickname('')
    setRole('ADULT')
    setConsent(false)
  }

  if (created) {
    return (
      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-medium">
          {intl.formatMessage({ id: 'invite.linkTitle' }, { name: created.nickname })}
        </h2>
        <InvitationLink
          token={created.token}
          hint={intl.formatMessage({ id: 'invite.linkHint' })}
        />
        <button type="button" onClick={another} className={`${button} border border-primary text-primary`}>
          {intl.formatMessage({ id: 'invite.another' })}
        </button>
        <Link to="/mais/familia" className={`${button} text-primary`}>
          {intl.formatMessage({ id: 'invite.done' })}
        </Link>
      </section>
    )
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'invite.nickname' })}</span>
        <input
          value={nickname}
          maxLength={40}
          onChange={(event) => setNickname(event.target.value)}
          className={field}
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'invite.role' })}</span>
        <select
          value={role}
          onChange={(event) => setRole(event.target.value as MemberRole)}
          className={field}
        >
          {roles.map((option) => (
            <option key={option} value={option}>
              {intl.formatMessage({ id: `members.role.${option}` })}
            </option>
          ))}
        </select>
      </label>
      {isYouth && (
        <label className="flex items-start gap-3">
          <input
            type="checkbox"
            checked={consent}
            onChange={(event) => setConsent(event.target.checked)}
            className="mt-1 size-5"
          />
          <span>{intl.formatMessage({ id: 'invite.consent' })}</span>
        </label>
      )}
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      {!online && <p className="text-text-muted">{intl.formatMessage({ id: 'members.offline' })}</p>}
      <button type="submit" disabled={!canCreate} className={`${button} bg-primary text-on-primary`}>
        {intl.formatMessage({ id: 'invite.create' })}
      </button>
    </form>
  )
}
