import { lazy, Suspense, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import {
  ApiError,
  useCurrentWorkspace,
  type InvitationView,
  type MeWorkspace,
  type MemberView,
} from '@/shared/api'
import { familyFeaturesOpen, usePlan } from '@/shared/api/billing'
import { useInvitations, useMembers, useRevokeInvitation } from '@/shared/api/family'
import { useModuleEnabled } from '@/shared/modules'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { MemberRow } from './MemberRow'
import { MemberSheet } from './MemberSheet'
import { canInvite, canOpenMember, canSetMarketHome } from './permissions'
import { RenameFamilySheet } from './RenameFamilySheet'
import './register'

// The address section belongs to the Feira module: its code and its requests wait for a family that
// switched the module on, so the family screen stays as light as before.
const MarketHomeSection = lazy(() =>
  import('./MarketHomeSection').then((module) => ({ default: module.MarketHomeSection })),
)

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const primary = `${button} bg-primary text-on-primary`
const outline = `${button} border border-primary text-primary`

/** The family: who is in it, who is waiting to join, and what the signed-in role may change. */
export function FamilyPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'members.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {workspace ? (
        <FamilyScreen workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function FamilyScreen({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const online = useOnline()
  const members = useMembers(workspace.id)
  const invites = canInvite(workspace.role)
  const marketsOn = useModuleEnabled('markets')
  // The address is a Feira setting, so it follows the Feira's plan rule; a plan that cannot be read
  // leaves it to the server, which refuses what the plan does not allow.
  const askPlan = marketsOn && canSetMarketHome(workspace.role)
  const plan = usePlan(askPlan ? workspace.id : undefined)
  const marketHomeOpen = askPlan && (plan.data ? familyFeaturesOpen(plan.data) : plan.isError)
  const [renaming, setRenaming] = useState(false)
  const [openId, setOpenId] = useState<string>()

  if (members.isPending) {
    return (
      <p>
        <FormattedMessage id="common.loading" />
      </p>
    )
  }
  if (members.isError) {
    return (
      <p role="alert">
        <FormattedMessage id="members.loadFailed" />
      </p>
    )
  }

  const active = members.data.filter((member) => member.status === 'ACTIVE')
  const removed = members.data.filter((member) => member.status === 'REMOVED')
  const opened = members.data.find((member) => member.id === openId)

  return (
    <>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-medium">{workspace.name}</h2>
        {workspace.role === 'OWNER' && (
          <button
            type="button"
            disabled={!online}
            onClick={() => setRenaming(true)}
            className={outline}
          >
            {intl.formatMessage({ id: 'members.rename' })}
          </button>
        )}
      </div>

      {invites && !online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'members.offline' })}</p>
      )}

      <ul className="flex flex-col divide-y divide-border">
        {active.map((member) => (
          <li key={member.id}>
            <MemberRow
              member={member}
              onOpen={
                canOpenMember(workspace.role, member) ? () => setOpenId(member.id) : undefined
              }
            />
          </li>
        ))}
      </ul>

      {invites && (
        <Link to="/mais/familia/convidar" className={primary}>
          {intl.formatMessage({ id: 'members.invite' })}
        </Link>
      )}

      {invites && <PendingInvitations workspaceId={workspace.id} />}

      {marketHomeOpen && (
        <Suspense fallback={null}>
          <MarketHomeSection />
        </Suspense>
      )}

      {removed.length > 0 && <RemovedMembers members={removed} />}

      {renaming && <RenameFamilySheet workspace={workspace} onClose={() => setRenaming(false)} />}
      {opened && (
        <MemberSheet
          key={opened.id}
          workspaceId={workspace.id}
          viewerRole={workspace.role}
          member={opened}
          onClose={() => setOpenId(undefined)}
        />
      )}
    </>
  )
}

/** Past members stay in the history, so they are listed, but folded away. */
function RemovedMembers({ members }: { members: MemberView[] }) {
  const intl = useIntl()
  return (
    <details>
      <summary className="flex min-h-12 cursor-pointer items-center font-medium">
        {intl.formatMessage({ id: 'members.removedSection' })}
      </summary>
      <ul className="flex flex-col divide-y divide-border">
        {members.map((member) => (
          <li key={member.id}>
            <MemberRow member={member} />
          </li>
        ))}
      </ul>
    </details>
  )
}

function PendingInvitations({ workspaceId }: { workspaceId: string }) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const invitations = useInvitations(workspaceId)
  const revoke = useRevokeInvitation(workspaceId)
  const [error, setError] = useState<string>()

  const pending = (invitations.data ?? []).filter((invitation) => invitation.kind === 'INVITE')
  if (pending.length === 0) return null

  async function revokeInvitation(invitation: InvitationView) {
    setError(undefined)
    try {
      await revoke.mutateAsync(invitation.id)
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'members.revoked' }) })
  }

  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-lg font-medium">{intl.formatMessage({ id: 'members.pending' })}</h2>
      <ul className="flex flex-col divide-y divide-border">
        {pending.map((invitation) => (
          <li key={invitation.id} className="flex min-h-12 items-center justify-between gap-2">
            <span>
              <span className="block font-medium">
                {invitation.nickname ?? intl.formatMessage({ id: 'invite.unnamed' })}
              </span>
              <span className="block text-sm text-text-muted">
                {intl.formatMessage({ id: `members.role.${invitation.role}` })}
                {' · '}
                {intl.formatMessage(
                  { id: 'members.pending.until' },
                  { date: intl.formatDate(invitation.expiresAt, { dateStyle: 'short' }) },
                )}
              </span>
            </span>
            <button
              type="button"
              disabled={!online || revoke.isPending}
              onClick={() => void revokeInvitation(invitation)}
              className={outline}
            >
              {intl.formatMessage({ id: 'members.revoke' })}
            </button>
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
    </section>
  )
}
