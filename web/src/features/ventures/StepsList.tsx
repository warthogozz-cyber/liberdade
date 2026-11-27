import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useIntl } from 'react-intl'
import type { MeWorkspace } from '@/shared/api'
import { useStepMutations, type StepView, type VentureView } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { venturesMessages } from './messages.pt-BR'

registerMessages(venturesMessages)

const MAX_STEPS = 20
const MAX_TITLE_LENGTH = 80

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const iconButton = `flex size-12 items-center justify-center rounded-field text-text-muted ${focus}`

/** Where the focus goes once the list shows the change the person just asked for. */
type FocusTarget =
  | { kind: 'move'; stepId: string; control: 'up' | 'down' }
  | { kind: 'after-remove'; index: number }
  | { kind: 'rename'; stepId: string }

interface StepsListProps {
  workspace: MeWorkspace
  venture: VentureView
  /** Without it the steps are plain text: done or not, nothing to press. */
  canWrite: boolean
}

/**
 * The project's steps. A person who can write ticks them, renames, moves them with buttons (not
 * only by dragging), adds and removes; the focus follows the change. Everyone else reads them.
 */
export function StepsList({ workspace, venture, canWrite }: StepsListProps) {
  const intl = useIntl()
  const steps = [...venture.steps].sort((a, b) => a.position - b.position)
  const { add, update, remove } = useStepMutations(workspace.id, venture.id)
  const [editing, setEditing] = useState<string | undefined>()
  const [draft, setDraft] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [failed, setFailed] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const addField = useRef<HTMLInputElement>(null)
  const pending = useRef<FocusTarget | undefined>(undefined)

  function applyFocus() {
    const target = pending.current
    pending.current = undefined
    if (!target || !root.current) return
    if (target.kind === 'move') {
      root.current
        .querySelector<HTMLElement>(
          `[data-step="${target.stepId}"] [data-control="${target.control}"]`,
        )
        ?.focus()
    } else if (target.kind === 'rename') {
      root.current
        .querySelector<HTMLElement>(`[data-step="${target.stepId}"] [data-control="rename"]`)
        ?.focus()
    } else {
      const next = steps[target.index]
      const control = next
        ? root.current.querySelector<HTMLElement>(`[data-step="${next.id}"] input[type="checkbox"]`)
        : addField.current
      control?.focus()
    }
  }

  // The order or the list changes only when the API's answer is in; the focus is put back then.
  useEffect(() => {
    applyFocus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [venture.steps])
  // Leaving the rename field, by saving or by giving up, returns to its button at once.
  useEffect(() => {
    if (editing === undefined) applyFocus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing])

  function run(target: FocusTarget | undefined, mutation: () => Promise<unknown>) {
    setFailed(false)
    pending.current = target
    mutation().catch(() => {
      pending.current = undefined
      setFailed(true)
    })
  }

  function move(step: StepView, index: number, delta: -1 | 1) {
    const to = index + delta
    if (to < 0 || to >= steps.length) return
    run({ kind: 'move', stepId: step.id, control: delta < 0 ? 'up' : 'down' }, () =>
      update.mutateAsync({ stepId: step.id, body: { position: to } }),
    )
  }

  function startRename(step: StepView) {
    setDraft(step.title)
    setEditing(step.id)
  }

  function saveRename(event: FormEvent, step: StepView) {
    event.preventDefault()
    const title = draft.trim()
    pending.current = { kind: 'rename', stepId: step.id }
    setEditing(undefined)
    if (title !== '' && title !== step.title) {
      run({ kind: 'rename', stepId: step.id }, () =>
        update.mutateAsync({ stepId: step.id, body: { title } }),
      )
    }
  }

  function cancelRename(event: KeyboardEvent, step: StepView) {
    if (event.key !== 'Escape') return
    event.stopPropagation()
    pending.current = { kind: 'rename', stepId: step.id }
    setEditing(undefined)
  }

  function addStep(event: FormEvent) {
    event.preventDefault()
    const title = newTitle.trim()
    if (title === '') return
    run(undefined, () => add.mutateAsync({ title }).then(() => setNewTitle('')))
  }

  const atLimit = steps.length >= MAX_STEPS

  return (
    <section
      ref={root}
      aria-labelledby="venture-steps-title"
      className="flex flex-col gap-3 rounded-card bg-surface p-4"
    >
      <h2 id="venture-steps-title" className="font-semibold">
        {intl.formatMessage({ id: 'ventures.steps.title' })}
      </h2>
      <p className="text-sm text-text-muted">{intl.formatMessage({ id: 'ventures.steps.help' })}</p>
      {steps.length === 0 && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.steps.empty' })}</p>
      )}
      <ul className="flex flex-col gap-1">
        {steps.map((step, index) => (
          <li key={step.id} data-step={step.id} className="flex items-center gap-1">
            {!canWrite ? (
              <span className="flex flex-1 justify-between gap-3 py-2">
                <span>{step.title}</span>
                <span className="text-text-muted">
                  {intl.formatMessage({
                    id: step.doneAt !== null ? 'ventures.steps.done' : 'ventures.steps.todo',
                  })}
                </span>
              </span>
            ) : editing === step.id ? (
              <form onSubmit={(event) => saveRename(event, step)} className="flex flex-1 gap-2">
                <input
                  type="text"
                  aria-label={intl.formatMessage({ id: 'ventures.steps.name' })}
                  value={draft}
                  maxLength={MAX_TITLE_LENGTH}
                  required
                  autoFocus
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => cancelRename(event, step)}
                  className="min-h-12 min-w-0 flex-1 rounded-field border border-text-muted/30 px-3"
                />
                <button type="submit" className={`min-h-12 px-3 font-medium text-primary ${focus}`}>
                  {intl.formatMessage({ id: 'ventures.steps.save' })}
                </button>
              </form>
            ) : (
              <>
                <label className="flex min-h-12 flex-1 items-center gap-3">
                  <input
                    type="checkbox"
                    checked={step.doneAt !== null}
                    onChange={(event) =>
                      run(undefined, () =>
                        update.mutateAsync({
                          stepId: step.id,
                          body: { done: event.target.checked },
                        }),
                      )
                    }
                    className="size-5 accent-primary"
                  />
                  <span className={step.doneAt !== null ? 'text-text-muted' : ''}>
                    {step.title}
                  </span>
                </label>
                <button
                  type="button"
                  data-control="rename"
                  aria-label={intl.formatMessage(
                    { id: 'ventures.steps.rename' },
                    { title: step.title },
                  )}
                  onClick={() => startRename(step)}
                  className={iconButton}
                >
                  <span aria-hidden="true">✎</span>
                </button>
                <button
                  type="button"
                  data-control="up"
                  aria-disabled={index === 0}
                  aria-label={intl.formatMessage(
                    { id: 'ventures.steps.moveUp' },
                    { title: step.title },
                  )}
                  onClick={() => move(step, index, -1)}
                  className={`${iconButton} ${index === 0 ? 'opacity-50' : ''}`}
                >
                  <span aria-hidden="true">↑</span>
                </button>
                <button
                  type="button"
                  data-control="down"
                  aria-disabled={index === steps.length - 1}
                  aria-label={intl.formatMessage(
                    { id: 'ventures.steps.moveDown' },
                    { title: step.title },
                  )}
                  onClick={() => move(step, index, 1)}
                  className={`${iconButton} ${index === steps.length - 1 ? 'opacity-50' : ''}`}
                >
                  <span aria-hidden="true">↓</span>
                </button>
                <button
                  type="button"
                  aria-label={intl.formatMessage(
                    { id: 'ventures.steps.remove' },
                    { title: step.title },
                  )}
                  onClick={() =>
                    run({ kind: 'after-remove', index }, () => remove.mutateAsync(step.id))
                  }
                  className={iconButton}
                >
                  <span aria-hidden="true">✕</span>
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      {canWrite && (
        <form onSubmit={addStep} className="flex gap-2">
          <input
            ref={addField}
            type="text"
            aria-label={intl.formatMessage({ id: 'ventures.steps.new' })}
            value={newTitle}
            maxLength={MAX_TITLE_LENGTH}
            disabled={atLimit}
            onChange={(event) => setNewTitle(event.target.value)}
            className="min-h-12 min-w-0 flex-1 rounded-field border border-text-muted/30 px-3"
          />
          <button
            type="submit"
            disabled={atLimit || add.isPending || newTitle.trim() === ''}
            className={`min-h-12 rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-50 ${focus}`}
          >
            {intl.formatMessage({ id: 'ventures.steps.add' })}
          </button>
        </form>
      )}
      {canWrite && atLimit && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.steps.limit' })}</p>
      )}
      {failed && <p role="alert">{intl.formatMessage({ id: 'common.actionFailed' })}</p>}
    </section>
  )
}
