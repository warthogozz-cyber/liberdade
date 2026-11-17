import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { CategoryView } from '@/shared/api'
import { COLOR_DOT } from '@/features/categories/colorDot'
import { Sheet } from '@/shared/ui/Sheet'
import { categoryLabel } from './listItems'

interface CategoryPickerProps {
  /** The categories of the tab's kind, in the API's order. The archived ones are left out. */
  categories: CategoryView[]
  value: string | undefined
  /** An archived category that stays on offer: the one the edited transaction already has (R9). */
  keepArchivedId?: string
  onChange: (categoryId: string) => void
  /** Tells the page a sheet is open, so the keypad leaves the keyboard alone. */
  onSheetChange: (open: boolean) => void
}

const CHIPS = 6

const chip =
  'flex min-h-12 cursor-pointer items-center rounded-field border border-text-muted/30 px-3 has-checked:border-primary has-checked:bg-primary has-checked:text-on-primary has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary'

/**
 * The category as radio chips: the six first, plus the chosen one when it is not among them. Mais
 * opens every category in a sheet.
 */
export function CategoryPicker({
  categories: all,
  value,
  keepArchivedId,
  onChange,
  onSheetChange,
}: CategoryPickerProps) {
  const intl = useIntl()
  const [open, setOpen] = useState(false)
  const categories = all.filter(
    (category) => category.archivedAt === null || category.id === keepArchivedId,
  )
  const archived = intl.formatMessage({ id: 'transactions.filter.archived' })
  const label = (category: CategoryView) =>
    category.archivedAt === null
      ? categoryLabel(category)
      : `${categoryLabel(category)} ${archived}`
  const first = categories.slice(0, CHIPS)
  const chosen = categories.find((category) => category.id === value)
  const chips = chosen && !first.includes(chosen) ? [...first, chosen] : first

  function setSheet(next: boolean) {
    setOpen(next)
    onSheetChange(next)
  }

  return (
    <fieldset>
      <legend className="mb-2 font-medium">
        <FormattedMessage id="expense.category" />
      </legend>
      <div className="flex flex-wrap gap-2">
        {chips.map((category) => (
          <label key={category.id} className={chip}>
            <input
              type="radio"
              name="category"
              value={category.id}
              checked={category.id === value}
              onChange={() => onChange(category.id)}
              className="sr-only"
            />
            {category.color && (
              <span
                aria-hidden="true"
                className={`mr-2 size-2.5 shrink-0 rounded-full ring-1 ring-surface ${COLOR_DOT[category.color]}`}
              />
            )}
            {label(category)}
          </label>
        ))}
        <button
          type="button"
          onClick={() => setSheet(true)}
          className="min-h-12 rounded-field px-3 font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="expense.moreCategories" />
        </button>
      </div>
      <Sheet
        open={open}
        onOpenChange={setSheet}
        title={intl.formatMessage({ id: 'expense.allCategories' })}
      >
        <ul className="grid grid-cols-2 gap-2">
          {categories.map((category) => (
            <li key={category.id}>
              <button
                type="button"
                onClick={() => {
                  onChange(category.id)
                  setSheet(false)
                }}
                className="min-h-12 w-full rounded-field border border-text-muted/30 px-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                {label(category)}
              </button>
            </li>
          ))}
        </ul>
      </Sheet>
    </fieldset>
  )
}
