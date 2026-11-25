import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import { Link, useNavigate } from 'react-router'
import { ApiError, type MeWorkspace } from '@/shared/api'
import { useChangeVentureStatus, useDeleteVenture, type VentureView } from '@/shared/api/ventures'
import { MIN_DATE, localDate, registerMessages } from '@/shared/i18n'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Sheet } from '@/shared/ui/Sheet'
import { venturesMessages } from './messages.pt-BR'

registerMessages(venturesMessages)

type Status = VentureView['status']
type MessageId = keyof typeof venturesMessages

interface Transition {
  to: Status
  /** The message id of the button. */
  label: MessageId
  /** The date the API asks for: when it started, or when it closed. */
  dateLabel?: MessageId
}

const START: Transition = {
  to: 'ACTIVE',
  label: 'ventures.action.start',
  dateLabel: 'ventures.form.startedOn',
}
const CLOSE: Transition = {
  to: 'CLOSED',
  label: 'ventures.action.close',
  dateLabel: 'ventures.statusSheet.closedOn',
}

/** What a project may do next, as the spec's table of status changes (6.5). */
const TRANSITIONS: Record<Status, Transition[]> = {
  PLANNING: [START, CLOSE],
  ACTIVE: [{ to: 'PAUSED', label: 'ventures.action.pause' }, CLOSE],
  PAUSED: [{ to: 'ACTIVE', label: 'ventures.action.resume' }, CLOSE],
  CLOSED: [{ to: 'PAUSED', label: 'ventures.action.reopen' }],
}

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button = `min-h-12 rounded-field border border-primary px-4 font-medium text-primary ${focus}`

interface StatusSheetProps {
  workspace: MeWorkspace
  venture: VentureView
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Changes a project's status, or deletes it after asking. For people who can write. */
export function StatusSheet({ workspace, venture, open, onOpenChange }: StatusSheetProps) {
  const intl = useIntl()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [deleteFailed, setDeleteFailed] = useState(false)
  const remove = useDeleteVenture(workspace.id, venture.id)

  return (
    <>
      <Sheet
        open={open}
        onOpenChange={onOpenChange}
        title={intl.formatMessage({ id: 'ventures.statusSheet.title' })}
      >
        <StatusActions
          workspace={workspace}
          venture={venture}
          onDone={() => onOpenChange(false)}
          onDelete={() => {
            setDeleteFailed(false)
            setConfirming(true)
          }}
          deleteFailed={deleteFailed}
        />
      </Sheet>
      <ConfirmDialog
        open={confirming}
        title={intl.formatMessage({ id: 'ventures.statusSheet.deleteTitle' })}
        body={intl.formatMessage({ id: 'ventures.statusSheet.deleteBody' })}
        confirmLabel={intl.formatMessage({ id: 'ventures.statusSheet.deleteConfirm' })}
        cancelLabel={intl.formatMessage({ id: 'ventures.cancel' })}
        confirmDisabled={remove.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() =>
          remove.mutate(undefined, {
            onSuccess: () => void navigate('/mais/projetos'),
            onError: () => {
              setDeleteFailed(true)
              setConfirming(false)
            },
          })
        }
      />
    </>
  )
}

interface StatusActionsProps {
  workspace: MeWorkspace
  venture: VentureView
  onDone: () => void
  onDelete: () => void
  deleteFailed: boolean
}

/** The sheet's body: its own state starts clean each time the sheet opens. */
function StatusActions({ workspace, venture, onDone, onDelete, deleteFailed }: StatusActionsProps) {
  const intl = useIntl()
  const change = useChangeVentureStatus(workspace.id, venture.id)
  const today = localDate(new Date(), workspace.timezone)
  const [chosen, setChosen] = useState<Transition | undefined>()
  const [date, setDate] = useState(today)
  const [error, setError] = useState<'limit' | 'conflict' | 'failed' | undefined>()
  const dateField = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (chosen) dateField.current?.focus()
  }, [chosen])

  function send(next: Transition, on?: string) {
    setError(undefined)
    change.mutate(
      {
        status: next.to,
        ...(on !== undefined && { on }),
        version: venture.version,
      },
      {
        onSuccess: onDone,
        onError: (failure) => {
          if (failure instanceof ApiError && failure.code === 'venture.limit-reached') {
            setError('limit')
          } else if (failure instanceof ApiError && failure.status === 409) {
            setError('conflict')
          } else {
            setError('failed')
          }
        },
      },
    )
  }

  function choose(next: Transition) {
    if (next.dateLabel) {
      setError(undefined)
      setChosen(next)
    } else {
      send(next)
    }
  }

  function confirm(event: FormEvent) {
    event.preventDefault()
    if (chosen && date !== '') send(chosen, date)
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-text-muted">
        {intl.formatMessage({ id: 'ventures.page.status' })}:{' '}
        {intl.formatMessage({ id: `ventures.status.${venture.status}` })}
      </p>
      <div className="flex flex-col gap-2">
        {TRANSITIONS[venture.status].map((next) => (
          <button
            key={next.to}
            type="button"
            disabled={change.isPending}
            onClick={() => choose(next)}
            className={button}
          >
            {intl.formatMessage({ id: next.label })}
          </button>
        ))}
      </div>
      {chosen?.dateLabel && (
        <form onSubmit={confirm} className="flex flex-col gap-2">
          <label className="font-medium" htmlFor="venture-status-date">
            {intl.formatMessage({ id: chosen.dateLabel })}
          </label>
          <input
            id="venture-status-date"
            ref={dateField}
            type="date"
            required
            value={date}
            min={MIN_DATE}
            max={today}
            onChange={(event) => setDate(event.target.value)}
            className="min-h-12 rounded-field border border-text-muted/30 px-3"
          />
          <button
            type="submit"
            disabled={change.isPending || date === ''}
            className={`min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-50 ${focus}`}
          >
            {intl.formatMessage({ id: 'ventures.statusSheet.confirm' })}
          </button>
        </form>
      )}
      {error === 'limit' && (
        <p role="alert">
          {intl.formatMessage(
            { id: 'ventures.limitReached' },
            {
              link: (chunks) => (
                <Link to="/mais/plano" className={`font-medium text-primary underline ${focus}`}>
                  {chunks}
                </Link>
              ),
            },
          )}
        </p>
      )}
      {error === 'conflict' && (
        <p role="alert">{intl.formatMessage({ id: 'ventures.statusSheet.conflict' })}</p>
      )}
      {(error === 'failed' || deleteFailed) && (
        <p role="alert">{intl.formatMessage({ id: 'common.actionFailed' })}</p>
      )}
      <button
        type="button"
        onClick={onDelete}
        className={`self-start min-h-12 px-2 font-medium text-text-muted underline ${focus}`}
      >
        {intl.formatMessage({ id: 'ventures.statusSheet.delete' })}
      </button>
    </div>
  )
}
