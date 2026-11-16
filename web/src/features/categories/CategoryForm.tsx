import { useId, useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import {
  ApiError,
  useCreateCategory,
  useUpdateCategory,
  type CategoryColor,
  type CategoryKind,
  type CategoryView,
  type MeWorkspace,
  type UpdateCategoryRequest,
} from '@/shared/api'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { CATEGORY_COLORS, CATEGORY_ICONS } from './categoryStyle'
import './register'

const MAX_NAME_LENGTH = 40

interface CategoryFormProps {
  workspace: MeWorkspace
  /** The kind of the tab: the new category's kind, and the kind of the parents on offer. */
  kind: CategoryKind
  /** Every category of `kind`, archived ones included, in the API's order. */
  categories: CategoryView[]
  /** The category being edited: its name, icon and color. Without one, the form creates one. */
  category?: CategoryView
  /** Called after a save, once the notice is shown. */
  onDone: () => void
}

const focusRing =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const option =
  'flex size-12 cursor-pointer items-center justify-center rounded-field border border-text-muted/30 text-2xl has-checked:border-primary has-checked:bg-primary/10 has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary has-disabled:cursor-not-allowed has-disabled:opacity-50'

/** Creates a category or subcategory, or edits a category's name, icon and color. */
export function CategoryForm({ workspace, kind, categories, category, onDone }: CategoryFormProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const create = useCreateCategory(workspace.id)
  const update = useUpdateCategory(workspace.id)
  const [name, setName] = useState(category?.name ?? '')
  const [icon, setIcon] = useState<string | null>(category?.icon ?? null)
  const [color, setColor] = useState<CategoryColor | null>(category?.color ?? null)
  const [parentId, setParentId] = useState('')
  const [nameError, setNameError] = useState<string>()
  const [error, setError] = useState<string>()
  const ids = useId()
  const nameErrorId = `${ids}-name-error`
  const iconLegendId = `${ids}-icon-legend`
  const colorLegendId = `${ids}-color-legend`
  const pending = create.isPending || update.isPending

  const parents = categories.filter(
    (candidate) => candidate.parentId === null && candidate.archivedAt === null,
  )
  const parentName = category?.parentId
    ? categories.find((candidate) => candidate.id === category.parentId)?.name
    : undefined
  // An icon from outside the list (an older category's) stays on offer, first.
  const unknownIcon =
    category?.icon && !CATEGORY_ICONS.some((entry) => entry.icon === category.icon)
      ? category.icon
      : undefined

  const trimmed = name.trim()
  // Only what changed is sent (partial PATCH). The API cannot clear an icon or a color, so only a
  // chosen one counts as a change. The kind and the parent never change (D7).
  const changes: UpdateCategoryRequest = {
    ...(category && trimmed !== category.name && { name: trimmed }),
    ...(category && icon !== null && icon !== category.icon && { icon }),
    ...(category && color !== null && color !== category.color && { color }),
  }
  const changed = Object.keys(changes).length > 0
  const canSave = online && !pending && trimmed !== '' && (!category || changed)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!canSave) return
    setNameError(undefined)
    setError(undefined)
    try {
      if (category) {
        await update.mutateAsync({ id: category.id, body: changes })
      } else {
        await create.mutateAsync({
          name: trimmed,
          kind,
          ...(icon !== null && { icon }),
          ...(color !== null && { color }),
          ...(parentId !== '' && { parentId }),
        })
      }
    } catch (failure) {
      const detail =
        (failure instanceof ApiError && failure.detail) ||
        intl.formatMessage({ id: 'common.actionFailed' })
      if (failure instanceof ApiError && failure.code === 'category.name-taken') {
        setNameError(detail)
      } else {
        setError(detail)
      }
      return
    }
    toast.show({
      message: intl.formatMessage({ id: category ? 'categories.saved' : 'categories.created' }),
    })
    onDone()
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'categories.field.name' })}</span>
        <input
          type="text"
          value={name}
          maxLength={MAX_NAME_LENGTH}
          autoComplete="off"
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? nameErrorId : undefined}
          onChange={(event) => {
            setName(event.target.value)
            setNameError(undefined)
          }}
          className="min-h-12 rounded-field border border-text-muted/30 bg-surface px-3"
        />
        {nameError && (
          <span id={nameErrorId} className="text-negative">
            {nameError}
          </span>
        )}
      </label>

      <fieldset role="radiogroup" aria-labelledby={iconLegendId}>
        <legend id={iconLegendId} className="mb-1 font-medium">
          {intl.formatMessage({ id: 'categories.field.icon' })}
        </legend>
        <div className="flex flex-wrap gap-2">
          {unknownIcon && (
            <label className={option}>
              <input
                type="radio"
                name={`${ids}-icon`}
                checked={icon === unknownIcon}
                onChange={() => setIcon(unknownIcon)}
                className="sr-only"
              />
              {unknownIcon}
            </label>
          )}
          {CATEGORY_ICONS.map((entry) => (
            <label key={entry.icon} className={option}>
              <input
                type="radio"
                name={`${ids}-icon`}
                aria-label={intl.formatMessage({ id: entry.labelId })}
                checked={icon === entry.icon}
                onChange={() => setIcon(entry.icon)}
                className="sr-only"
              />
              <span aria-hidden="true">{entry.icon}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset role="radiogroup" aria-labelledby={colorLegendId}>
        <legend id={colorLegendId} className="mb-1 font-medium">
          {intl.formatMessage({ id: 'categories.field.color' })}
        </legend>
        <div className="flex flex-wrap gap-2">
          <ColorOption
            name={`${ids}-color`}
            label={intl.formatMessage({ id: 'categories.color.none' })}
            checked={color === null}
            // The API cannot take a color away, so a colored category cannot go back to none.
            disabled={category?.color != null}
            onChange={() => setColor(null)}
          />
          {CATEGORY_COLORS.map((entry) => (
            <ColorOption
              key={entry.color}
              name={`${ids}-color`}
              label={intl.formatMessage({ id: entry.labelId })}
              dot={entry.dot}
              checked={color === entry.color}
              onChange={() => setColor(entry.color)}
            />
          ))}
        </div>
      </fieldset>

      {category ? (
        <dl className="flex flex-col gap-1">
          <div className="flex gap-2">
            <dt className="font-medium">{intl.formatMessage({ id: 'categories.field.kind' })}</dt>
            <dd className="text-text-muted">
              {intl.formatMessage({
                id: kind === 'EXPENSE' ? 'categories.tab.expense' : 'categories.tab.income',
              })}
            </dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-medium">{intl.formatMessage({ id: 'categories.field.parent' })}</dt>
            <dd className="text-text-muted">
              {parentName ?? intl.formatMessage({ id: 'categories.parent.none' })}
            </dd>
          </div>
        </dl>
      ) : (
        <label className="flex flex-col gap-1">
          <span className="font-medium">
            {intl.formatMessage({ id: 'categories.field.parent' })}
          </span>
          <select
            value={parentId}
            onChange={(event) => setParentId(event.target.value)}
            className="min-h-12 rounded-field border border-text-muted/30 bg-surface px-3"
          >
            <option value="">{intl.formatMessage({ id: 'categories.parent.none' })}</option>
            {parents.map((parent) => (
              <option key={parent.id} value={parent.id}>
                {parent.name}
              </option>
            ))}
          </select>
        </label>
      )}

      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      {!online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'categories.offline' })}</p>
      )}
      <button
        type="submit"
        disabled={!canSave}
        className={`min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-50 ${focusRing}`}
      >
        {intl.formatMessage({ id: 'categories.save' })}
      </button>
    </form>
  )
}

interface ColorOptionProps {
  name: string
  label: string
  /** The dot's classes; "Sem cor" has none and shows an empty ring. */
  dot?: string
  checked: boolean
  disabled?: boolean
  onChange: () => void
}

/** A color as a radio: a dot, named by its `aria-label`; the name shows as a tag while it has focus. */
function ColorOption({ name, label, dot, checked, disabled, onChange }: ColorOptionProps) {
  return (
    <label className={`${option} relative`}>
      <input
        type="radio"
        name={name}
        aria-label={label}
        checked={checked}
        disabled={disabled}
        onChange={onChange}
        className="peer sr-only"
      />
      <span
        aria-hidden="true"
        className={`size-6 rounded-full ${dot ?? 'border-2 border-dashed border-text-muted'}`}
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -top-9 left-1/2 z-10 hidden -translate-x-1/2 rounded-field bg-text px-2 py-1 text-sm whitespace-nowrap text-bg peer-focus-visible:block"
      >
        {label}
      </span>
    </label>
  )
}
