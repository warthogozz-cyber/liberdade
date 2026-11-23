import { useId, useRef, useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import {
  ApiError,
  useArchiveTag,
  useRenameTag,
  useTags,
  type MeWorkspace,
  type TagView,
} from '@/shared/api'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Sheet } from '@/shared/ui/Sheet'
import './register'

type View = { kind: 'rename' | 'archive'; tag: TagView }

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const outline = `${button} border border-primary text-primary`

function failureText(failure: unknown, fallback: string): string {
  return (failure instanceof ApiError && failure.detail) || fallback
}

/** The sheet's form: the tag's name, changed. A name another tag has stays on the field. */
function RenameForm({
  workspace,
  tag,
  onDone,
}: {
  workspace: MeWorkspace
  tag: TagView
  onDone: () => void
}) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const rename = useRenameTag(workspace.id)
  const errorId = useId()
  const [name, setName] = useState(tag.name)
  const [nameError, setNameError] = useState<string>()
  const [error, setError] = useState<string>()
  const trimmed = name.trim()
  const canSave = online && !rename.isPending && trimmed !== '' && trimmed !== tag.name

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!canSave) return
    setNameError(undefined)
    setError(undefined)
    try {
      await rename.mutateAsync({ id: tag.id, name: trimmed })
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === 'tag.name-taken') {
        setNameError(intl.formatMessage({ id: 'tags.nameTaken' }))
      } else {
        setError(failureText(failure, intl.formatMessage({ id: 'common.actionFailed' })))
      }
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'tags.renamedToast' }) })
    onDone()
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'tags.field.name' })}</span>
        <input
          type="text"
          value={name}
          autoComplete="off"
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? errorId : undefined}
          onChange={(event) => {
            setName(event.target.value)
            setNameError(undefined)
          }}
          className="min-h-12 rounded-field border border-text-muted/30 bg-surface px-3"
        />
        {nameError && (
          <span id={errorId} className="text-negative">
            {nameError}
          </span>
        )}
      </label>
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      <button type="submit" disabled={!canSave} className={`${button} bg-primary text-on-primary`}>
        <FormattedMessage id="categories.save" />
      </button>
    </form>
  )
}

/**
 * The family's active tags by name. An Owner or an Adult renames or archives them; a Viewer only
 * reads. Loaded on demand by the categories screen.
 */
export default function TagsTab({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const tags = useTags(workspace.id)
  const archive = useArchiveTag(workspace.id)
  const archiving = useRef(false)
  const [view, setView] = useState<View | null>(null)
  const [archiveError, setArchiveError] = useState<string>()
  const canManage = workspace.role === 'OWNER' || workspace.role === 'ADULT'

  const sorted = [...(tags.data ?? [])]
    .filter((tag) => tag.archivedAt === null)
    .sort((x, y) => x.name.localeCompare(y.name, intl.locale))

  async function confirmArchive(tag: TagView) {
    if (archiving.current || archive.isPending) return
    archiving.current = true
    try {
      await archive.mutateAsync(tag.id)
    } catch (failure) {
      setArchiveError(failureText(failure, intl.formatMessage({ id: 'common.actionFailed' })))
      setView(null)
      return
    } finally {
      archiving.current = false
    }
    toast.show({ message: intl.formatMessage({ id: 'tags.archivedToast' }) })
    setView(null)
  }

  if (tags.isPending) {
    return (
      <p>
        <FormattedMessage id="common.loading" />
      </p>
    )
  }
  if (tags.isError && !tags.data) {
    return (
      <div className="flex flex-col items-start gap-2">
        <p>
          <FormattedMessage id="common.error" />
        </p>
        <button type="button" onClick={() => void tags.refetch()} className={outline}>
          <FormattedMessage id="common.retry" />
        </button>
      </div>
    )
  }

  return (
    <>
      {sorted.length === 0 ? (
        <p className="text-text-muted">
          <FormattedMessage id="tags.empty" />
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {sorted.map((tag) => (
            <li
              key={tag.id}
              className="flex min-h-14 items-center gap-2 rounded-card bg-surface p-3"
            >
              <span className="min-w-0 flex-1 truncate font-medium">{tag.name}</span>
              {canManage && (
                <>
                  <button
                    type="button"
                    disabled={!online}
                    aria-label={`${intl.formatMessage({ id: 'tags.rename' })} ${tag.name}`}
                    onClick={() => setView({ kind: 'rename', tag })}
                    className={outline}
                  >
                    <FormattedMessage id="tags.rename" />
                  </button>
                  <button
                    type="button"
                    disabled={!online}
                    aria-label={`${intl.formatMessage({ id: 'categories.archive' })} ${tag.name}`}
                    onClick={() => {
                      setArchiveError(undefined)
                      setView({ kind: 'archive', tag })
                    }}
                    className={`${button} border border-negative text-negative`}
                  >
                    <FormattedMessage id="categories.archive" />
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {archiveError && (
        <p role="alert" className="text-negative">
          {archiveError}
        </p>
      )}
      {view && (
        <>
          <Sheet
            open={view.kind === 'rename'}
            onOpenChange={(next) => {
              if (!next) setView(null)
            }}
            title={intl.formatMessage({ id: 'tags.rename.title' }, { name: view.tag.name })}
          >
            <RenameForm workspace={workspace} tag={view.tag} onDone={() => setView(null)} />
          </Sheet>
          <ConfirmDialog
            open={view.kind === 'archive'}
            title={intl.formatMessage({ id: 'tags.archive.title' }, { name: view.tag.name })}
            body={intl.formatMessage({ id: 'tags.archive.body' })}
            confirmLabel={<FormattedMessage id="categories.archive" />}
            cancelLabel={<FormattedMessage id="categories.cancel" />}
            confirmDisabled={archive.isPending}
            onConfirm={() => void confirmArchive(view.tag)}
            onCancel={() => setView(null)}
          />
        </>
      )}
    </>
  )
}
