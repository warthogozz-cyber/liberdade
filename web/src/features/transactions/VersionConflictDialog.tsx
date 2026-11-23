import { AlertDialog } from 'radix-ui'
import { useIntl } from 'react-intl'
import {
  useFormatMoney,
  type AccountView,
  type CategoryView,
  type TransactionKind,
  type TransactionView,
  type UpdateTransactionRequest,
} from '@/shared/api'
import type { messages } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import { categoryLabel } from './listItems'

interface VersionConflictDialogProps {
  open: boolean
  /** What the person tried to save. */
  saved: UpdateTransactionRequest
  /** The stored transaction, from the API's `current` (Ruling R22). */
  current: TransactionView
  categories: Map<string, CategoryView>
  accounts: Map<string, AccountView>
  onKeepTheirs: () => void
  onKeepMine: () => void
}

const KIND = {
  EXPENSE: 'transaction.kind.expense',
  INCOME: 'transaction.kind.income',
  TRANSFER: 'transaction.kind.transfer',
} as const satisfies Record<TransactionKind, string>

const button =
  'min-h-12 rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * The stored version differs from the one being saved: shows only the fields that differ, as the
 * stored version has them, and asks which to keep (D12). The focus starts on keeping the stored one.
 */
export function VersionConflictDialog({
  open,
  saved,
  current,
  categories,
  accounts,
  onKeepTheirs,
  onKeepMine,
}: VersionConflictDialogProps) {
  const intl = useIntl()
  const format = useFormatMoney()
  const text = (id: MessageId) => intl.formatMessage({ id })
  const date = (value: string) =>
    intl.formatDate(`${value}T00:00:00Z`, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'UTC',
    })
  const accountName = (id: string | null | undefined) => (id ? (accounts.get(id)?.name ?? '') : '')
  const categoryName = (id: string | null | undefined) => {
    const category = id ? categories.get(id) : undefined
    return category ? categoryLabel(category) : ''
  }

  const differences: [label: string, value: string][] = []
  if (saved.kind !== current.kind) {
    differences.push([text('transactions.detail.kind'), text(KIND[current.kind])])
  }
  if (parseCents(saved.amount.amount) !== parseCents(current.amount.amount)) {
    differences.push([
      text('transactions.detail.amount'),
      format(parseCents(current.amount.amount)),
    ])
  }
  if (saved.occurredOn !== current.occurredOn) {
    differences.push([text('transactions.detail.date'), date(current.occurredOn)])
  }
  if (saved.accountId !== current.accountId) {
    differences.push([
      text(
        current.kind === 'TRANSFER' ? 'transactions.detail.from' : 'transactions.detail.account',
      ),
      accountName(current.accountId),
    ])
  }
  if ((saved.toAccountId ?? null) !== (current.toAccountId ?? null)) {
    differences.push([text('transactions.detail.to'), accountName(current.toAccountId)])
  }
  if ((saved.categoryId ?? null) !== (current.categoryId ?? null)) {
    differences.push([text('transactions.detail.category'), categoryName(current.categoryId)])
  }
  if ((saved.description ?? null) !== (current.description ?? null)) {
    differences.push([text('transactions.detail.description'), current.description ?? '—'])
  }

  return (
    <AlertDialog.Root open={open}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 bg-black/40" />
        <AlertDialog.Content className="fixed inset-x-4 top-1/2 mx-auto max-w-sm -translate-y-1/2 rounded-sheet bg-surface p-6 shadow-lg">
          <AlertDialog.Title className="text-lg font-semibold">
            {text('conflict.title')}
          </AlertDialog.Title>
          <AlertDialog.Description className="mt-2 text-text-muted">
            {text('conflict.body')}
          </AlertDialog.Description>
          <p className="mt-4 font-medium">{text('conflict.current')}</p>
          <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
            {differences.map(([label, value]) => (
              <div key={label} className="contents">
                <dt className="text-text-muted">{label}</dt>
                <dd className="text-right">{value}</dd>
              </div>
            ))}
          </dl>
          <div className="mt-6 flex flex-col gap-2">
            <AlertDialog.Cancel
              className={`${button} border border-primary text-primary`}
              onClick={onKeepTheirs}
            >
              {text('conflict.keepTheirs')}
            </AlertDialog.Cancel>
            <AlertDialog.Action
              className={`${button} bg-primary text-on-primary`}
              onClick={(event) => {
                event.preventDefault()
                onKeepMine()
              }}
            >
              {text('conflict.keepMine')}
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}

/** The transaction is gone (deleted by someone else): the only way on is back to the list. */
export function DeletedElsewhereDialog({ open, onBack }: { open: boolean; onBack: () => void }) {
  const intl = useIntl()
  return (
    <AlertDialog.Root open={open}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 bg-black/40" />
        <AlertDialog.Content
          aria-describedby={undefined}
          className="fixed inset-x-4 top-1/2 mx-auto max-w-sm -translate-y-1/2 rounded-sheet bg-surface p-6 shadow-lg"
        >
          <AlertDialog.Title className="text-lg font-semibold">
            {intl.formatMessage({ id: 'deletedElsewhere.title' })}
          </AlertDialog.Title>
          <div className="mt-6 flex flex-col gap-2">
            <AlertDialog.Action
              className={`${button} bg-primary text-on-primary`}
              onClick={(event) => {
                event.preventDefault()
                onBack()
              }}
            >
              {intl.formatMessage({ id: 'deletedElsewhere.back' })}
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}

type MessageId = keyof typeof messages
