import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, useFormatMoney, type AccountView, type MeWorkspace } from '@/shared/api'
import {
  useArchiveGoal,
  useContributions,
  useCreateGoal,
  useUpdateGoal,
  type CreateGoalRequest,
  type GoalView,
  type UpdateGoalRequest,
} from '@/shared/api/planning'
import { parseCents } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Sheet } from '@/shared/ui/Sheet'
import { ContributionSheet } from './ContributionSheet'
import { GoalFacts } from './GoalFacts'
import { GoalForm } from './GoalForm'
import { goalEmoji } from './goalIcons'
import './register'

interface GoalSheetProps {
  workspace: MeWorkspace
  /** The goal being looked at; without it, the sheet creates one. */
  goal?: GoalView
  /** The free plan's 3 active goals are in use: creating only explains that (D11). */
  limitReached: boolean
  accounts: readonly AccountView[]
  onClose: () => void
  /** The goal changed under the person (409): the list reloads it. */
  onConflict: () => void
}

const button =
  'min-h-12 rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const outline = `${button} border border-primary text-primary`

/** A new goal, or one goal's detail with its contributions, edit and archive. All of it needs the connection. */
export function GoalSheet({
  workspace,
  goal,
  limitReached,
  accounts,
  onClose,
  onConflict,
}: GoalSheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const format = useFormatMoney()
  const create = useCreateGoal(workspace.id)
  const update = useUpdateGoal(workspace.id)
  const archive = useArchiveGoal(workspace.id)
  const contributions = useContributions(workspace.id, goal && !goal.linked ? goal.id : '')
  const [editing, setEditing] = useState(false)
  const [contributing, setContributing] = useState<'save' | 'withdraw'>()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string>()
  const busy = create.isPending || update.isPending || archive.isPending

  function failed(failure: unknown) {
    const code = failure instanceof ApiError ? failure.code : undefined
    if (code === 'goal.limit-reached') {
      setError(intl.formatMessage({ id: 'planning.goals.limitReached' }))
    } else if (code === 'goal.due-in-past') {
      setError(intl.formatMessage({ id: 'planning.goals.duePast' }))
    } else if (failure instanceof ApiError && failure.status === 409) {
      onClose()
      onConflict()
      toast.show({ message: intl.formatMessage({ id: 'planning.goals.conflict' }) })
    } else {
      console.error(failure)
      setError(intl.formatMessage({ id: 'planning.goals.failed' }))
    }
  }

  async function createGoal(body: CreateGoalRequest) {
    if (!online || busy) return
    setError(undefined)
    try {
      await create.mutateAsync(body)
    } catch (failure) {
      failed(failure)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'planning.goals.sheet.saved' }) })
    onClose()
  }

  async function updateGoal(body: UpdateGoalRequest) {
    if (!online || busy || !goal) return
    setError(undefined)
    try {
      await update.mutateAsync({ id: goal.id, body })
    } catch (failure) {
      failed(failure)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'planning.goals.sheet.saved' }) })
    setEditing(false)
  }

  async function archiveGoal() {
    if (!online || !goal) return
    setConfirming(false)
    try {
      await archive.mutateAsync(goal.id)
    } catch (failure) {
      failed(failure)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'planning.goals.archived' }) })
    onClose()
  }

  const offline = !online && (
    <p className="text-text-muted">{intl.formatMessage({ id: 'planning.goals.offline' })}</p>
  )
  const archiveLabel = intl.formatMessage({ id: 'planning.goals.archive' })
  const accountName = goal?.linkedAccountId
    ? accounts.find((each) => each.id === goal.linkedAccountId)?.name
    : undefined

  let title = intl.formatMessage({ id: 'planning.goals.sheet.new' })
  let content
  if (!goal && limitReached) {
    content = <p>{intl.formatMessage({ id: 'planning.goals.limitReached' })}</p>
  } else if (!goal || editing) {
    title = intl.formatMessage({
      id: goal ? 'planning.goals.sheet.edit' : 'planning.goals.sheet.new',
    })
    content = (
      <>
        <GoalForm
          workspace={workspace}
          {...(goal ? { goal } : {})}
          accounts={accounts}
          saveDisabled={!online || busy}
          onCreate={(body) => void createGoal(body)}
          onUpdate={(body) => void updateGoal(body)}
          error={error}
        />
        {offline}
      </>
    )
  } else {
    title = `${goalEmoji(goal.icon)} ${goal.name}`
    content = (
      <>
        <div className="flex flex-col gap-1">
          <GoalFacts goal={goal} accountName={accountName} />
        </div>
        {!goal.linked && (
          <section aria-labelledby="goal-contributions" className="flex flex-col gap-2">
            <h3 id="goal-contributions" className="font-medium">
              {intl.formatMessage({ id: 'planning.goals.contributions' })}
            </h3>
            {(contributions.data ?? []).length === 0 ? (
              <p className="text-sm text-text-muted">
                {intl.formatMessage({ id: 'planning.goals.noContributions' })}
              </p>
            ) : (
              <ul className="flex flex-col gap-1">
                {(contributions.data ?? []).map((each) => {
                  const cents = parseCents(each.amount.amount)
                  return (
                    <li key={each.id} className="flex justify-between gap-4 text-sm">
                      <span className="text-text-muted">
                        {intl.formatDate(`${each.occurredOn}T00:00:00Z`, {
                          dateStyle: 'medium',
                          timeZone: 'UTC',
                        })}
                        {' · '}
                        {intl.formatMessage({
                          id:
                            cents < 0n
                              ? 'planning.goals.contribution.out'
                              : 'planning.goals.contribution.in',
                        })}
                      </span>
                      <span className="tabular-nums">{format(cents < 0n ? -cents : cents)}</span>
                    </li>
                  )
                })}
              </ul>
            )}
          </section>
        )}
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {offline}
        {!goal.linked && (
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              disabled={!online || busy}
              onClick={() => setContributing('save')}
              className={`${button} bg-primary text-on-primary`}
            >
              {intl.formatMessage({ id: 'planning.goals.save' })}
            </button>
            <button
              type="button"
              disabled={!online || busy}
              onClick={() => setContributing('withdraw')}
              className={outline}
            >
              {intl.formatMessage({ id: 'planning.goals.withdraw' })}
            </button>
          </div>
        )}
        <button
          type="button"
          disabled={!online || busy}
          onClick={() => setEditing(true)}
          className={outline}
        >
          {intl.formatMessage({ id: 'planning.goals.edit' })}
        </button>
        <button
          type="button"
          disabled={!online || busy}
          onClick={() => setConfirming(true)}
          className={`${button} border border-negative text-negative`}
        >
          {archiveLabel}
        </button>
      </>
    )
  }

  return (
    <>
      <Sheet
        open
        onOpenChange={(open) => {
          if (!open) onClose()
        }}
        title={title}
      >
        <div className="flex flex-col gap-4">{content}</div>
      </Sheet>
      {goal && contributing && (
        <ContributionSheet
          workspace={workspace}
          goal={goal}
          direction={contributing}
          onClose={() => setContributing(undefined)}
        />
      )}
      <ConfirmDialog
        open={confirming}
        title={archiveLabel}
        body={intl.formatMessage({ id: 'planning.goals.archiveBody' })}
        confirmLabel={archiveLabel}
        cancelLabel={intl.formatMessage({ id: 'planning.goals.archiveCancel' })}
        confirmDisabled={busy}
        onConfirm={() => void archiveGoal()}
        onCancel={() => setConfirming(false)}
      />
    </>
  )
}
