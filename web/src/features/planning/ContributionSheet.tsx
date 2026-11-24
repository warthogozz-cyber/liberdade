import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, type MeWorkspace } from '@/shared/api'
import { useAddContribution, type GoalView } from '@/shared/api/planning'
import { localDate } from '@/shared/i18n'
import { centsToApi, parseTypedAmount } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { Sheet } from '@/shared/ui/Sheet'
import './register'

interface ContributionSheetProps {
  workspace: MeWorkspace
  goal: GoalView
  /** Putting money aside, or taking some of it back. */
  direction: 'save' | 'withdraw'
  onClose: () => void
}

const field =
  'min-h-12 w-full rounded-field border border-primary bg-surface px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** Records an amount put into a goal, or taken out of it. It needs the connection. */
export function ContributionSheet({ workspace, goal, direction, onClose }: ContributionSheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const add = useAddContribution(workspace.id)
  const [text, setText] = useState('')
  const [error, setError] = useState<string>()
  const label = intl.formatMessage({
    id: direction === 'save' ? 'planning.goals.save' : 'planning.goals.withdraw',
  })

  function failed(failure: unknown) {
    const code = failure instanceof ApiError ? failure.code : undefined
    if (code === 'goal.negative-balance') {
      setError(intl.formatMessage({ id: 'planning.goals.contribute.negative' }))
    } else if (code === 'goal.linked-account') {
      setError(intl.formatMessage({ id: 'planning.goals.contribute.linked' }))
    } else {
      console.error(failure)
      setError(intl.formatMessage({ id: 'planning.goals.contribute.failed' }))
    }
  }

  async function submit() {
    if (!online || add.isPending) return
    const cents = parseTypedAmount(text)
    if (cents === undefined) {
      setError(intl.formatMessage({ id: 'planning.goals.contribute.invalid' }))
      return
    }
    setError(undefined)
    try {
      await add.mutateAsync({
        id: goal.id,
        body: {
          amount: {
            amount: centsToApi(direction === 'save' ? cents : -cents),
            currency: workspace.currency,
          },
          occurredOn: localDate(new Date(), workspace.timezone),
        },
      })
    } catch (failure) {
      failed(failure)
      return
    }
    toast.show({
      message: intl.formatMessage({
        id:
          direction === 'save'
            ? 'planning.goals.contribute.saved'
            : 'planning.goals.contribute.withdrawn',
      }),
    })
    onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage(
        {
          id:
            direction === 'save'
              ? 'planning.goals.contribute.save'
              : 'planning.goals.contribute.withdraw',
        },
        { name: goal.name },
      )}
    >
      <div className="flex flex-col gap-4">
        <label className="flex flex-col gap-1">
          <span className="font-medium">
            {intl.formatMessage({ id: 'planning.goals.contribute.amount' })}
          </span>
          <input
            inputMode="decimal"
            autoComplete="off"
            value={text}
            onChange={(event) => setText(event.target.value)}
            className={field}
          />
        </label>
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'planning.goals.offline' })}</p>
        )}
        <button
          type="button"
          disabled={!online || add.isPending}
          onClick={() => void submit()}
          className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
        >
          {label}
        </button>
      </div>
    </Sheet>
  )
}
