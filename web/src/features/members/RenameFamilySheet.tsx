import { useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, type MeWorkspace } from '@/shared/api'
import { useRenameWorkspace } from '@/shared/api/family'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { Sheet } from '@/shared/ui/Sheet'
import './register'

interface RenameFamilySheetProps {
  workspace: MeWorkspace
  onClose: () => void
}

/** The Owner gives the family another name. */
export function RenameFamilySheet({ workspace, onClose }: RenameFamilySheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const rename = useRenameWorkspace(workspace.id)
  const [name, setName] = useState(workspace.name)
  const [error, setError] = useState<string>()
  const trimmed = name.trim()

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!online || rename.isPending || trimmed === '') return
    setError(undefined)
    try {
      await rename.mutateAsync(trimmed)
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'members.renamed' }) })
    onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'members.rename.title' })}
    >
      <form onSubmit={(event) => void save(event)} className="flex flex-col gap-4">
        <label className="flex flex-col gap-1">
          <span className="font-medium">{intl.formatMessage({ id: 'members.rename.field' })}</span>
          <input
            value={name}
            maxLength={60}
            onChange={(event) => setName(event.target.value)}
            className="min-h-12 rounded-field border border-border bg-bg px-3"
          />
        </label>
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'members.offline' })}</p>
        )}
        <button
          type="submit"
          disabled={!online || rename.isPending || trimmed === ''}
          className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
        >
          {intl.formatMessage({ id: 'members.save' })}
        </button>
      </form>
    </Sheet>
  )
}
