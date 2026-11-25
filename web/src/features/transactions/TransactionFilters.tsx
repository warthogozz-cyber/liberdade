import { useIntl } from 'react-intl'
import type {
  AccountView,
  CategoryView,
  PaymentMethod,
  TagView,
  TransactionFilters as Filters,
} from '@/shared/api'
import type { VentureSummary } from '@/shared/api/ventures'
import { Sheet } from '@/shared/ui/Sheet'
import { categoryLabel } from './listItems'

interface TransactionFiltersProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  filters: Filters
  onChange: (filters: Filters) => void
  /** Every category, of both kinds, archived ones too: the list can hold transactions in them. */
  categories: CategoryView[]
  /** Every account, archived ones too. */
  accounts: AccountView[]
  /** The family's active tags. */
  tags: TagView[]
  /** The family's payment methods, in the order they are shown. */
  paymentMethods: PaymentMethod[]
  /** The projects to filter by, or null when the filter does not show (module off, Youth, none open). */
  ventures: VentureSummary[] | null
}

const option =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary'

/** Top-level categories each followed by its subcategories, in the order they came. */
function inTree(categories: CategoryView[]): CategoryView[] {
  const ids = new Set(categories.map(({ id }) => id))
  const roots = categories.filter(({ parentId }) => parentId === null || !ids.has(parentId))
  return roots.flatMap((root) => [
    root,
    ...categories.filter(({ parentId }) => parentId === root.id),
  ])
}

/** The sheet that narrows the list to a category, an account, a tag and a payment method; a tap applies at once. */
export function TransactionFilters({
  open,
  onOpenChange,
  filters,
  onChange,
  categories,
  accounts,
  tags,
  paymentMethods,
  ventures,
}: TransactionFiltersProps) {
  const intl = useIntl()
  const archived = intl.formatMessage({ id: 'transactions.filter.archived' })

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={intl.formatMessage({ id: 'transactions.filters' })}
    >
      <div className="flex flex-col gap-6">
        <fieldset>
          <legend className="mb-2 font-medium">
            {intl.formatMessage({ id: 'transactions.filter.category' })}
          </legend>
          {inTree(categories).map((category) => (
            <label
              key={category.id}
              className={`${option} ${category.parentId === null ? '' : 'ml-6'}`}
            >
              <input
                type="radio"
                name="filter-category"
                checked={filters.categoryId === category.id}
                onChange={() => onChange({ ...filters, categoryId: category.id })}
              />
              {category.archivedAt === null
                ? categoryLabel(category)
                : `${categoryLabel(category)} ${archived}`}
            </label>
          ))}
        </fieldset>
        <fieldset>
          <legend className="mb-2 font-medium">
            {intl.formatMessage({ id: 'transactions.filter.account' })}
          </legend>
          {accounts.map((account) => (
            <label key={account.id} className={option}>
              <input
                type="radio"
                name="filter-account"
                checked={filters.accountId === account.id}
                onChange={() => onChange({ ...filters, accountId: account.id })}
              />
              {account.archivedAt === null ? account.name : `${account.name} ${archived}`}
            </label>
          ))}
        </fieldset>
        <fieldset>
          <legend className="mb-2 font-medium">
            {intl.formatMessage({ id: 'transactions.filter.tag' })}
          </legend>
          {tags.map((tag) => (
            <label key={tag.id} className={option}>
              <input
                type="radio"
                name="filter-tag"
                checked={filters.tagId === tag.id}
                onChange={() => onChange({ ...filters, tagId: tag.id })}
              />
              {tag.name}
            </label>
          ))}
        </fieldset>
        <fieldset>
          <legend className="mb-2 font-medium">
            {intl.formatMessage({ id: 'payment.label' })}
          </legend>
          {paymentMethods.map((method) => (
            <label key={method} className={option}>
              <input
                type="radio"
                name="filter-payment-method"
                checked={filters.paymentMethod === method}
                onChange={() => onChange({ ...filters, paymentMethod: method })}
              />
              {intl.formatMessage({ id: `payment.${method}` })}
            </label>
          ))}
        </fieldset>
        {ventures && (
          <fieldset>
            <legend className="mb-2 font-medium">
              {intl.formatMessage({ id: 'transactions.filter.venture' })}
            </legend>
            {ventures.map((venture) => (
              <label key={venture.id} className={option}>
                <input
                  type="radio"
                  name="filter-venture"
                  checked={filters.ventureId === venture.id}
                  onChange={() => onChange({ ...filters, ventureId: venture.id })}
                />
                {venture.status === 'CLOSED'
                  ? intl.formatMessage({ id: 'transaction.venture.closed' }, { name: venture.name })
                  : venture.name}
              </label>
            ))}
          </fieldset>
        )}
      </div>
    </Sheet>
  )
}
