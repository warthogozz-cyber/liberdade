import { useEffect, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useAccounts, useCurrentWorkspace, type MeWorkspace } from '@/shared/api'
import { useGoals, type GoalView } from '@/shared/api/planning'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { celebrateNew, useCelebrationQueue } from './celebration'
import { GoalCelebrations } from './GoalCelebration'
import { GoalFacts } from './GoalFacts'
import { goalEmoji } from './goalIcons'
import { GoalSheet } from './GoalSheet'
import './register'

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The family's goals (spec 6.6): active ones first, reached ones below. */
export function GoalsPage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'planning.goals.title' }))
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="planning.goals.title" />
      </h1>
      {workspace ? (
        <GoalsContent workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

type Opened = { kind: 'new' } | { kind: 'goal'; id: string }

function GoalsContent({ workspace }: { workspace: MeWorkspace }) {
  const online = useOnline()
  const goals = useGoals(workspace.id)
  const editable = workspace.role === 'OWNER' || workspace.role === 'ADULT'
  // A Youth never gets the linked account (US-10), so the accounts are not even asked for.
  const accounts = useAccounts(workspace.id, { enabled: editable })
  const [opened, setOpened] = useState<Opened>()
  const { items, add, remove } = useCelebrationQueue()

  // Opening the screen, and every refresh of the list, celebrates what was reached recently and
  // was not celebrated yet; older ones are only remembered (D20).
  const data = goals.data
  useEffect(() => {
    if (!data) return
    const reached = celebrateNew(workspace.id, data.goals, new Date())
    if (reached.length > 0) add(reached)
  }, [data, workspace.id, add])

  const list = data?.goals ?? []
  const active = list.filter((each) => each.achievedAt === null)
  const achieved = list.filter((each) => each.achievedAt !== null)
  const accountList = accounts.data ?? []
  const openedGoal =
    opened?.kind === 'goal' ? list.find((each) => each.id === opened.id) : undefined

  return (
    <>
      {goals.isPending ? (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      ) : data === undefined ? (
        <p>
          <FormattedMessage id="planning.goals.loadFailed" />
        </p>
      ) : list.length === 0 ? (
        <p className="rounded-card bg-surface p-4">
          <FormattedMessage id="planning.goals.empty" />
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {[...active, ...achieved].map((goal) => (
            <li key={goal.id}>
              <GoalCard
                goal={goal}
                accountName={accountList.find((each) => each.id === goal.linkedAccountId)?.name}
                {...(editable ? { onOpen: () => setOpened({ kind: 'goal', id: goal.id }) } : {})}
              />
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <div className="flex flex-col gap-3">
          <button
            type="button"
            disabled={!online}
            onClick={() => setOpened({ kind: 'new' })}
            className={`min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary ${focus} disabled:opacity-50`}
          >
            <FormattedMessage id="planning.goals.new" />
          </button>
          {!online && (
            <p className="text-sm text-text-muted">
              <FormattedMessage id="planning.goals.offline" />
            </p>
          )}
        </div>
      )}
      {opened?.kind === 'new' && (
        <GoalSheet
          workspace={workspace}
          limitReached={data?.limitReached ?? false}
          accounts={accountList}
          onClose={() => setOpened(undefined)}
          onConflict={() => void goals.refetch()}
        />
      )}
      {openedGoal && (
        <GoalSheet
          workspace={workspace}
          goal={openedGoal}
          limitReached={false}
          accounts={accountList}
          onClose={() => setOpened(undefined)}
          onConflict={() => void goals.refetch()}
        />
      )}
      <GoalCelebrations items={items} onDone={remove} />
    </>
  )
}

interface GoalCardProps {
  goal: GoalView
  accountName: string | undefined
  /** Opens the goal; without it the card is only read (a Youth). */
  onOpen?: () => void
}

function GoalCard({ goal, accountName, onOpen }: GoalCardProps) {
  const intl = useIntl()
  const reached = goal.achievedAt !== null
  const body = (
    <>
      <span className="flex items-baseline justify-between gap-4">
        <span className="flex min-w-0 items-center gap-2">
          <span aria-hidden="true">{goalEmoji(goal.icon)}</span>
          <span className="truncate font-medium">{goal.name}</span>
        </span>
        <span className="tabular-nums text-text-muted">
          {intl.formatMessage({ id: 'planning.goals.percent' }, { percent: goal.percent })}
        </span>
      </span>
      <span
        role="meter"
        aria-label={goal.name}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={goal.percent}
        className="block h-2 overflow-hidden rounded-full bg-text-muted/20"
      >
        <span
          className="block h-full rounded-full bg-primary"
          style={{ width: `${Math.min(100, goal.percent)}%` }}
        />
      </span>
      <GoalFacts goal={goal} accountName={accountName} />
      {reached && (
        <span className="text-sm font-medium text-positive">
          {intl.formatMessage({ id: 'planning.goals.achieved' })}
        </span>
      )}
    </>
  )
  const box = 'flex min-h-14 w-full flex-col gap-1 rounded-card bg-surface p-4 text-left'
  return onOpen ? (
    <button type="button" onClick={onOpen} className={`${box} ${focus}`}>
      {body}
    </button>
  ) : (
    <div className={box}>{body}</div>
  )
}
