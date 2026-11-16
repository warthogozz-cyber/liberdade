import { useId, useRef, useState } from 'react'
import { Navigate } from 'react-router'
import { FormattedMessage, useIntl } from 'react-intl'
import {
  ApiError,
  useArchiveCategory,
  useCategories,
  useCurrentWorkspace,
  useUnarchiveCategory,
  type CategoryKind,
  type CategoryView,
  type MeWorkspace,
} from '@/shared/api'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Sheet } from '@/shared/ui/Sheet'
import { CategoryForm } from './CategoryForm'
import { CategoryTabs } from './CategoryTabs'
import { categoryTabId, categoryTree } from './categoryStyle'
import { COLOR_DOT } from './colorDot'
import './register'

type View = { kind: 'new' } | { kind: 'edit' | 'archive'; id: string }

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const primary = `${button} bg-primary text-on-primary`
const outline = `${button} border border-primary text-primary`

/** The family's categories and subcategories, by kind: add, edit, archive and bring back. */
export function CategoriesPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'categories.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  // A Youth's menu has no such page; one reached by its address goes back to the menu.
  if (workspace?.role === 'YOUTH') return <Navigate to="/mais" replace />

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {workspace ? (
        <CategoriesScreen workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

/** The text of a failed call: the API's own, or the generic one when it had none. */
function failureText(failure: unknown, fallback: string): string {
  return (failure instanceof ApiError && failure.detail) || fallback
}

/** The icon and name of a category, with its color dot when it has one. */
function CategoryLabel({ category }: { category: CategoryView }) {
  return (
    <>
      {category.color && (
        <span
          aria-hidden="true"
          className={`size-3 shrink-0 rounded-full ${COLOR_DOT[category.color]}`}
        />
      )}
      {category.icon && (
        <span aria-hidden="true" className="text-2xl">
          {category.icon}
        </span>
      )}
      <span className="min-w-0 flex-1 truncate font-medium">{category.name}</span>
    </>
  )
}

function CategoriesScreen({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const [kind, setKind] = useState<CategoryKind>('EXPENSE')
  const panelId = useId()
  const categories = useCategories(workspace.id, kind)
  const archive = useArchiveCategory(workspace.id)
  const unarchive = useUnarchiveCategory(workspace.id)
  const archiving = useRef(false)
  const [view, setView] = useState<View | null>(null)
  const [archiveError, setArchiveError] = useState<string>()
  const [unarchiveError, setUnarchiveError] = useState<string>()

  const all = categories.data ?? []
  const active = all.filter((category) => category.archivedAt === null)
  const archived = all.filter((category) => category.archivedAt !== null)
  const archivedTree = categoryTree(archived, kind)
  const selected =
    view && view.kind !== 'new' ? all.find((category) => category.id === view.id) : undefined
  const generic = intl.formatMessage({ id: 'common.actionFailed' })

  function open(id: string) {
    setArchiveError(undefined)
    setView({ kind: 'edit', id })
  }

  async function confirmArchive(category: CategoryView) {
    if (archiving.current || archive.isPending) return
    archiving.current = true
    try {
      await archive.mutateAsync(category.id)
    } catch (failure) {
      setArchiveError(failureText(failure, generic))
      setView({ kind: 'edit', id: category.id })
      return
    } finally {
      archiving.current = false
    }
    toast.show({ message: intl.formatMessage({ id: 'categories.archivedToast' }) })
    setView(null)
  }

  async function reactivate(category: CategoryView) {
    if (unarchive.isPending) return
    setUnarchiveError(undefined)
    try {
      await unarchive.mutateAsync(category.id)
    } catch (failure) {
      setUnarchiveError(failureText(failure, generic))
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'categories.unarchivedToast' }) })
  }

  function changeKind(next: CategoryKind) {
    setKind(next)
    setUnarchiveError(undefined)
  }

  return (
    <>
      <CategoryTabs value={kind} onChange={changeKind} panelId={panelId} />
      <div
        role="tabpanel"
        id={panelId}
        aria-labelledby={categoryTabId(panelId, kind)}
        className="flex flex-col gap-4"
      >
        <button
          type="button"
          disabled={!online}
          onClick={() => setView({ kind: 'new' })}
          className={primary}
        >
          <FormattedMessage id="categories.new" />
        </button>
        {!online && (
          <p role="status" className="text-text-muted">
            <FormattedMessage id="categories.offline" />
          </p>
        )}
        {categories.isPending ? (
          <p>
            <FormattedMessage id="common.loading" />
          </p>
        ) : categories.isError && all.length === 0 ? (
          <div className="flex flex-col items-start gap-2">
            <p>
              <FormattedMessage id="common.error" />
            </p>
            <button type="button" onClick={() => void categories.refetch()} className={outline}>
              <FormattedMessage id="common.retry" />
            </button>
          </div>
        ) : (
          <ul className="flex flex-col gap-2">
            {categoryTree(active, kind).map(({ category, children }) => (
              <li key={category.id} className="flex flex-col gap-1">
                <button
                  type="button"
                  onClick={() => open(category.id)}
                  className="flex min-h-14 w-full items-center gap-3 rounded-card bg-surface p-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <CategoryLabel category={category} />
                </button>
                {children.length > 0 && (
                  <ul className="flex flex-col gap-1 pl-6">
                    {children.map((child) => (
                      <li key={child.id}>
                        <button
                          type="button"
                          onClick={() => open(child.id)}
                          className="flex min-h-12 w-full items-center gap-3 rounded-card bg-surface p-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                        >
                          <CategoryLabel category={child} />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
        {archived.length > 0 && (
          <details className="rounded-card bg-surface p-3">
            <summary className="flex min-h-12 cursor-pointer items-center font-medium">
              {intl.formatMessage({ id: 'categories.archived' }, { count: archivedTree.length })}
            </summary>
            <ul className="mt-2 flex flex-col gap-2">
              {archivedTree.map(({ category, children }) => (
                <li key={category.id} className="flex flex-col gap-1">
                  <div className="flex items-center gap-3">
                    <CategoryLabel category={category} />
                    <button
                      type="button"
                      disabled={!online || unarchive.isPending}
                      onClick={() => void reactivate(category)}
                      className={outline}
                    >
                      <FormattedMessage id="categories.unarchive" />
                    </button>
                  </div>
                  {children.length > 0 && (
                    <ul className="flex flex-col gap-1 pl-6">
                      {children.map((child) => (
                        <li key={child.id} className="flex items-center gap-3">
                          <CategoryLabel category={child} />
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
            {unarchiveError && (
              <p role="alert" className="mt-2 text-negative">
                {unarchiveError}
              </p>
            )}
          </details>
        )}
      </div>

      <Sheet
        open={view?.kind === 'new'}
        onOpenChange={(next) => {
          if (!next) setView(null)
        }}
        title={intl.formatMessage({ id: 'categories.new' })}
      >
        <CategoryForm
          workspace={workspace}
          kind={kind}
          categories={all}
          onDone={() => setView(null)}
        />
      </Sheet>
      {selected && (
        <>
          <Sheet
            open={view?.kind === 'edit'}
            onOpenChange={(next) => {
              if (!next) setView(null)
            }}
            title={selected.name}
          >
            <div key={selected.id} className="flex flex-col gap-4">
              <CategoryForm
                workspace={workspace}
                kind={kind}
                categories={all}
                category={selected}
                onDone={() => setView(null)}
              />
              <button
                type="button"
                disabled={!online}
                onClick={() => setView({ kind: 'archive', id: selected.id })}
                className={`${button} border border-negative text-negative`}
              >
                <FormattedMessage id="categories.archive" />
              </button>
              {archiveError && (
                <p role="alert" className="text-negative">
                  {archiveError}
                </p>
              )}
            </div>
          </Sheet>
          <ConfirmDialog
            open={view?.kind === 'archive'}
            title={intl.formatMessage({ id: 'categories.archive.title' }, { name: selected.name })}
            body={intl.formatMessage(
              { id: 'categories.archive.body' },
              { children: active.filter((child) => child.parentId === selected.id).length },
            )}
            confirmLabel={<FormattedMessage id="categories.archive" />}
            cancelLabel={<FormattedMessage id="categories.cancel" />}
            confirmDisabled={archive.isPending}
            onConfirm={() => void confirmArchive(selected)}
            onCancel={() => {
              setArchiveError(undefined)
              setView({ kind: 'edit', id: selected.id })
            }}
          />
        </>
      )}
    </>
  )
}
