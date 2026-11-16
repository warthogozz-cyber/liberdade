import { useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, ReauthCancelledError, type MeWorkspace } from '@/shared/api'
import { useFamilyDeletion } from '@/shared/api/privacy'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { Sheet } from '@/shared/ui'
import { dangerButton } from './buttons'
import './register'

/** The Owner schedules the family's deletion by typing its name. */
export function DeleteFamilySheet({
  workspace,
  onClose,
}: {
  workspace: Pick<MeWorkspace, 'id' | 'name'>
  onClose: () => void
}) {
  const intl = useIntl()
  const online = useOnline()
  const run = useReauth()
  const { request } = useFamilyDeletion(workspace.id)
  const [typed, setTyped] = useState('')
  const [error, setError] = useState<string>()
  const matches = typed.trim() === workspace.name

  async function schedule(event: FormEvent) {
    event.preventDefault()
    if (!online || !matches || request.isPending) return
    setError(undefined)
    try {
      await run(() => request.mutateAsync())
    } catch (failure) {
      if (failure instanceof ReauthCancelledError) return
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'privacy.deleteFailed' }),
      )
      return
    }
    // `me` now carries the date: the family's screens give way to the pending card.
    onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'privacy.deleteFamily.title' })}
    >
      <form onSubmit={(event) => void schedule(event)} className="flex flex-col gap-4">
        <p>{intl.formatMessage({ id: 'privacy.deleteFamily.body' })}</p>
        <label className="flex flex-col gap-1">
          <span className="font-medium">
            {intl.formatMessage({ id: 'privacy.deleteFamily.field' }, { name: workspace.name })}
          </span>
          <input
            value={typed}
            autoComplete="off"
            onChange={(event) => setTyped(event.target.value)}
            className="min-h-12 rounded-field border border-border bg-bg px-3"
          />
        </label>
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.offline' })}</p>
        )}
        <button
          type="submit"
          disabled={!online || !matches || request.isPending}
          className={dangerButton}
        >
          {intl.formatMessage({ id: 'privacy.deleteFamily.confirm' })}
        </button>
      </form>
    </Sheet>
  )
}
