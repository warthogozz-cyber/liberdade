import { useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import {
  ApiError,
  ReauthCancelledError,
  type MemberRole,
  type MemberView,
} from '@/shared/api'
import { useCreateRecoveryLink, useRemoveMember, useUpdateMember } from '@/shared/api/family'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { ConfirmDialog, Sheet, useToast } from '@/shared/ui'
import { memberName } from './memberName'
import { canRecover } from './permissions'
import { RecoveryLinkSheet } from './RecoveryLinkSheet'
import './register'

interface MemberSheetProps {
  workspaceId: string
  /** Who is looking: the Owner manages, the Adult can only bring a passkey-only member back. */
  viewerRole: MemberRole
  member: MemberView
  onClose: () => void
}

const ROLES: MemberRole[] = ['OWNER', 'ADULT', 'YOUTH', 'VIEWER']

const field = 'min-h-12 rounded-field border border-border bg-bg px-3'
const primary =
  'min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const secondary =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** A member's details: nickname and role, removal, and the recovery link. */
export function MemberSheet({ workspaceId, viewerRole, member, onClose }: MemberSheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const run = useReauth()
  const update = useUpdateMember(workspaceId)
  const remove = useRemoveMember(workspaceId)
  const recovery = useCreateRecoveryLink(workspaceId)
  const isOwner = viewerRole === 'OWNER'
  const name = memberName(member, intl)

  const [nickname, setNickname] = useState(member.nickname ?? '')
  const [role, setRole] = useState<MemberRole>(member.role)
  const [consent, setConsent] = useState(false)
  const [confirmingRemoval, setConfirmingRemoval] = useState(false)
  const [error, setError] = useState<string>()
  const [recoveryToken, setRecoveryToken] = useState<string>()

  const becomesYouth = role === 'YOUTH' && member.role !== 'YOUTH'
  const busy = update.isPending || remove.isPending || recovery.isPending
  const actionable = online && !busy

  function fail(failure: unknown) {
    // Cancelling the confirmation is the person's choice, not a failure.
    if (failure instanceof ReauthCancelledError) return
    if (failure instanceof ApiError && failure.code === 'member.last-owner') {
      setError(intl.formatMessage({ id: 'members.lastOwner' }))
      return
    }
    setError(
      (failure instanceof ApiError && failure.detail) ||
        intl.formatMessage({ id: 'common.actionFailed' }),
    )
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!actionable) return
    setError(undefined)
    const trimmed = nickname.trim()
    try {
      await run(() =>
        update.mutateAsync({
          id: member.id,
          body: {
            nickname: trimmed === '' ? null : trimmed,
            role,
            ...(becomesYouth ? { parentalConsent: consent } : {}),
          },
        }),
      )
    } catch (failure) {
      fail(failure)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'members.saved' }) })
    onClose()
  }

  async function confirmRemoval() {
    setError(undefined)
    try {
      await run(() => remove.mutateAsync(member.id))
    } catch (failure) {
      setConfirmingRemoval(false)
      fail(failure)
      return
    }
    setConfirmingRemoval(false)
    toast.show({ message: intl.formatMessage({ id: 'members.removedToast' }) })
    onClose()
  }

  async function makeRecoveryLink() {
    setError(undefined)
    try {
      const created = await run(() => recovery.mutateAsync(member.id))
      if (created.token) setRecoveryToken(created.token)
    } catch (failure) {
      fail(failure)
    }
  }

  if (recoveryToken) return <RecoveryLinkSheet token={recoveryToken} onClose={onClose} />

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={name}
    >
      <div className="flex flex-col gap-4">
        {isOwner && (
          <form onSubmit={(event) => void save(event)} className="flex flex-col gap-4">
            <label className="flex flex-col gap-1">
              <span className="font-medium">{intl.formatMessage({ id: 'members.field.nickname' })}</span>
              <input
                value={nickname}
                maxLength={40}
                onChange={(event) => setNickname(event.target.value)}
                className={field}
              />
            </label>
            <label className="flex flex-col gap-1">
              <span className="font-medium">{intl.formatMessage({ id: 'members.field.role' })}</span>
              <select
                value={role}
                onChange={(event) => setRole(event.target.value as MemberRole)}
                className={field}
              >
                {ROLES.map((option) => (
                  <option key={option} value={option}>
                    {intl.formatMessage({ id: `members.role.${option}` })}
                  </option>
                ))}
              </select>
            </label>
            {becomesYouth && (
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  checked={consent}
                  onChange={(event) => setConsent(event.target.checked)}
                  className="mt-1 size-5"
                />
                <span>{intl.formatMessage({ id: 'members.consent' })}</span>
              </label>
            )}
            <button type="submit" disabled={!actionable || (becomesYouth && !consent)} className={primary}>
              {intl.formatMessage({ id: 'members.save' })}
            </button>
          </form>
        )}

        {canRecover(viewerRole, member) && (
          <button
            type="button"
            disabled={!actionable}
            onClick={() => void makeRecoveryLink()}
            className={secondary}
          >
            {intl.formatMessage({ id: 'members.recovery' })}
          </button>
        )}

        {isOwner && (
          <button
            type="button"
            disabled={!actionable}
            onClick={() => setConfirmingRemoval(true)}
            className={`${secondary} border-negative text-negative`}
          >
            {intl.formatMessage({ id: 'members.remove' })}
          </button>
        )}

        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'members.offline' })}</p>
        )}
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
      </div>

      <ConfirmDialog
        open={confirmingRemoval}
        title={intl.formatMessage({ id: 'members.remove.title' }, { name })}
        body={intl.formatMessage({ id: 'members.remove.body' })}
        confirmLabel={intl.formatMessage({ id: 'members.remove' })}
        cancelLabel={intl.formatMessage({ id: 'members.cancel' })}
        confirmDisabled={busy}
        onConfirm={() => void confirmRemoval()}
        onCancel={() => setConfirmingRemoval(false)}
      />
    </Sheet>
  )
}
