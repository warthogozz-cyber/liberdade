import { useState } from 'react'
import { useIntl } from 'react-intl'
import type { AccountView, MeWorkspace } from '@/shared/api'
import type { CreateGoalRequest, GoalView, UpdateGoalRequest } from '@/shared/api/planning'
import { currentMonth } from '@/shared/i18n'
import { centsToApi, parseCents, parseTypedAmount, typedAmount } from '@/shared/money'
import { GOAL_ICON_KEYS, goalEmoji } from './goalIcons'
import './register'

const MAX_NAME_LENGTH = 40

const field =
  'min-h-12 w-full rounded-field border border-primary bg-surface px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

interface GoalFormProps {
  workspace: MeWorkspace
  /** The goal being changed; without it, a new goal. */
  goal?: GoalView
  /** The family's accounts; only the active ones in the base currency can be followed. */
  accounts: readonly AccountView[]
  saveDisabled: boolean
  /** The body to create with, or the one to change `goal` with, only what changed. */
  onCreate: (body: CreateGoalRequest) => void
  onUpdate: (body: UpdateGoalRequest) => void
  /** Shown above the button, such as a failure from the API. */
  error?: string | undefined
}

/**
 * The fields of a goal. A goal that already has a deadline can lose it: "Sem prazo" sends
 * `clearDueOn`, since an empty month alone means "leave it as it is".
 */
export function GoalForm({
  workspace,
  goal,
  accounts,
  saveDisabled,
  onCreate,
  onUpdate,
  error,
}: GoalFormProps) {
  const intl = useIntl()
  const [name, setName] = useState(goal?.name ?? '')
  const [icon, setIcon] = useState(goal?.icon ?? 'savings')
  const [target, setTarget] = useState(
    goal ? typedAmount(parseCents(goal.targetAmount.amount)) : '',
  )
  const [due, setDue] = useState(goal?.dueOn ?? '')
  const [noDeadline, setNoDeadline] = useState(false)
  const [linkedAccountId, setLinkedAccountId] = useState('')
  const [invalid, setInvalid] = useState<string>()
  const followable = accounts.filter(
    (each) => each.archivedAt === null && each.currency === workspace.currency,
  )
  const hasDeadline = goal?.dueOn != null

  function save() {
    const cents = parseTypedAmount(target)
    const trimmed = name.trim()
    if (trimmed === '' || trimmed.length > MAX_NAME_LENGTH || cents === undefined) {
      setInvalid(intl.formatMessage({ id: 'planning.goals.form.invalid' }))
      return
    }
    setInvalid(undefined)
    const targetAmount = { amount: centsToApi(cents), currency: workspace.currency }
    if (!goal) {
      onCreate({
        name: trimmed,
        icon,
        targetAmount,
        ...(due !== '' ? { dueOn: due } : {}),
        ...(linkedAccountId !== '' ? { linkedAccountId } : {}),
      })
      return
    }
    onUpdate({
      version: goal.version,
      name: trimmed,
      icon,
      targetAmount,
      // Only a changed deadline goes out; an unchanged one stays as it is, and "Sem prazo" clears it.
      ...(noDeadline
        ? { clearDueOn: true }
        : due !== '' && due !== goal.dueOn
          ? { dueOn: due }
          : {}),
    })
  }

  const shown = invalid ?? error
  return (
    <div className="flex flex-col gap-4">
      <label className="flex flex-col gap-1">
        <span className="font-medium">
          {intl.formatMessage({ id: 'planning.goals.form.name' })}
        </span>
        <input
          value={name}
          maxLength={MAX_NAME_LENGTH}
          autoComplete="off"
          onChange={(event) => setName(event.target.value)}
          className={field}
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="font-medium">
          {intl.formatMessage({ id: 'planning.goals.form.icon' })}
        </span>
        <select value={icon} onChange={(event) => setIcon(event.target.value)} className={field}>
          {GOAL_ICON_KEYS.map((key) => (
            <option key={key} value={key}>
              {`${goalEmoji(key)} ${intl.formatMessage({ id: `planning.goals.icon.${key}` })}`}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1">
        <span className="font-medium">
          {intl.formatMessage({ id: 'planning.goals.form.target' })}
        </span>
        <input
          inputMode="decimal"
          autoComplete="off"
          value={target}
          onChange={(event) => setTarget(event.target.value)}
          className={field}
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="font-medium">
          {intl.formatMessage({
            id: hasDeadline ? 'planning.goals.form.dueKeep' : 'planning.goals.form.due',
          })}
        </span>
        <input
          type="month"
          value={noDeadline ? '' : due}
          disabled={noDeadline}
          min={currentMonth(new Date(), workspace.timezone)}
          onChange={(event) => setDue(event.target.value)}
          className={field}
        />
      </label>
      {hasDeadline && (
        <label className="flex min-h-12 items-center gap-3">
          <input
            type="checkbox"
            checked={noDeadline}
            onChange={(event) => setNoDeadline(event.target.checked)}
            className="size-5 accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
          <span>{intl.formatMessage({ id: 'planning.goals.form.noDeadline' })}</span>
        </label>
      )}
      {!goal && followable.length > 0 && (
        <label className="flex flex-col gap-1">
          <span className="font-medium">
            {intl.formatMessage({ id: 'planning.goals.form.linked' })}
          </span>
          <select
            value={linkedAccountId}
            onChange={(event) => setLinkedAccountId(event.target.value)}
            className={field}
          >
            <option value="">{intl.formatMessage({ id: 'planning.goals.form.noAccount' })}</option>
            {followable.map((each) => (
              <option key={each.id} value={each.id}>
                {each.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {shown && (
        <p role="alert" className="text-negative">
          {shown}
        </p>
      )}
      <button
        type="button"
        disabled={saveDisabled}
        onClick={save}
        className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        {intl.formatMessage({ id: 'planning.goals.form.save' })}
      </button>
    </div>
  )
}
