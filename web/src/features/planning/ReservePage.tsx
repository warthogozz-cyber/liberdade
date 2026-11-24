import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useCurrentWorkspace, type MeWorkspace } from '@/shared/api'
import { useReserve, useUpdateReserve, type ReserveView } from '@/shared/api/planning'
import { centsToApi, parseCents, parseTypedAmount, typedAmount } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { ReserveSummary } from './ReserveSummary'
import './register'

const MIN_TARGET = 1
const MAX_TARGET = 24
const TARGETS = Array.from(
  { length: MAX_TARGET - MIN_TARGET + 1 },
  (_, index) => MIN_TARGET + index,
)

const field =
  'min-h-12 w-full rounded-field border border-primary bg-surface px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-60'

/** The emergency reserve (spec 6.7): months covered, what is missing, the target and the typed cost. */
export function ReservePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'planning.reserve.title' }))
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="planning.reserve.title" />
      </h1>
      {workspace ? (
        <ReserveContent workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function ReserveContent({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const online = useOnline()
  const toast = useToast()
  const reserve = useReserve(workspace.id)
  const update = useUpdateReserve(workspace.id)
  const editable = workspace.role === 'OWNER' || workspace.role === 'ADULT'

  async function save(
    body: { targetMonths: number } | { manualMonthlyCost: ReserveView['manualMonthlyCost'] },
  ) {
    try {
      await update.mutateAsync(body)
    } catch (error) {
      console.error(error)
      toast.show({ message: intl.formatMessage({ id: 'planning.reserve.failed' }) })
      return false
    }
    toast.show({ message: intl.formatMessage({ id: 'planning.reserve.saved' }) })
    return true
  }

  if (reserve.isPending) {
    return (
      <p>
        <FormattedMessage id="common.loading" />
      </p>
    )
  }
  if (reserve.data === undefined) {
    return (
      <p>
        <FormattedMessage id="planning.reserve.loadFailed" />
      </p>
    )
  }
  const view = reserve.data

  return (
    <>
      <section className="flex flex-col gap-3 rounded-card bg-surface p-4">
        {view.costSource === 'NONE' ? (
          editable ? (
            <CostForm
              workspace={workspace}
              view={view}
              disabled={!online || update.isPending}
              onSave={(manualMonthlyCost) => save({ manualMonthlyCost })}
            />
          ) : (
            <p>
              <FormattedMessage id="planning.reserve.costViewer" />
            </p>
          )
        ) : (
          <ReserveSummary reserve={view} />
        )}
        {!view.hasReserveAccounts && (
          <p className="text-text-muted">
            <FormattedMessage id="planning.reserve.noAccounts" />
          </p>
        )}
      </section>
      {editable && (
        <section className="flex flex-col gap-4">
          <label className="flex flex-col gap-1">
            <span className="font-medium">
              <FormattedMessage id="planning.reserve.target" />
            </span>
            <select
              value={String(view.targetMonths)}
              disabled={!online || update.isPending}
              onChange={(event) => {
                const months = Number(event.target.value)
                if (Number.isInteger(months) && months >= MIN_TARGET && months <= MAX_TARGET) {
                  void save({ targetMonths: months })
                }
              }}
              className={field}
            >
              {TARGETS.map((months) => (
                <option key={months} value={String(months)}>
                  {intl.formatMessage({ id: 'planning.reserve.targetOption' }, { months })}
                </option>
              ))}
            </select>
          </label>
          {view.costSource === 'MANUAL' && (
            <CostForm
              workspace={workspace}
              view={view}
              disabled={!online || update.isPending}
              onSave={(manualMonthlyCost) => save({ manualMonthlyCost })}
            />
          )}
          {!online && (
            <p className="text-sm text-text-muted">
              <FormattedMessage id="planning.reserve.offline" />
            </p>
          )}
        </section>
      )}
      <p className="text-text-muted">
        <FormattedMessage id="planning.reserve.education" />
      </p>
    </>
  )
}

interface CostFormProps {
  workspace: MeWorkspace
  view: ReserveView
  disabled: boolean
  onSave: (cost: NonNullable<ReserveView['manualMonthlyCost']>) => Promise<boolean>
}

/** The monthly cost the family types in; it asks the question while there is no cost at all. */
function CostForm({ workspace, view, disabled, onSave }: CostFormProps) {
  const intl = useIntl()
  const [text, setText] = useState(
    view.manualMonthlyCost ? typedAmount(parseCents(view.manualMonthlyCost.amount)) : '',
  )
  const [invalid, setInvalid] = useState(false)
  const asking = view.costSource === 'NONE'

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (disabled) return
    const cents = parseTypedAmount(text)
    if (cents === undefined) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    await onSave({ amount: centsToApi(cents), currency: workspace.currency })
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-3">
      <p className={asking ? 'text-lg font-medium' : 'font-medium'}>
        {intl.formatMessage({
          id: asking ? 'planning.reserve.askCost' : 'planning.reserve.costTyped',
        })}
      </p>
      <input
        type="text"
        inputMode="decimal"
        autoComplete="off"
        aria-label={intl.formatMessage({ id: 'planning.reserve.costField' })}
        aria-invalid={invalid ? true : undefined}
        value={text}
        disabled={disabled}
        onChange={(event) => {
          setText(event.target.value)
          setInvalid(false)
        }}
        className={field}
      />
      {invalid && (
        <p role="alert" className="text-negative">
          <FormattedMessage id="planning.reserve.costInvalid" />
        </p>
      )}
      <button
        type="submit"
        disabled={disabled}
        className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        <FormattedMessage id="planning.reserve.costSave" />
      </button>
    </form>
  )
}
