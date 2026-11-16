import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import type { AccountView, CategoryView, MeWorkspace, MemberView } from '@/shared/api'
import { firstName, type messages } from '@/shared/i18n'
import { formatBRL, parseCents } from '@/shared/money'
import { ConfirmDialog, Sheet } from '@/shared/ui'
import { categoryLabel, type ListItem } from './listItems'
import { memberLabel } from './memberLabel'
import './more.register'

interface TransactionDetailSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  item: ListItem
  categories: Map<string, CategoryView>
  accounts: Map<string, AccountView>
  workspace: MeWorkspace
  displayName: string
  members?: Map<string, MemberView>
  online: boolean
  /** The month of the list, which Editar sends back to. */
  month: string
  onDelete: (id: string) => void
}

const KIND = {
  EXPENSE: 'transaction.kind.expense',
  INCOME: 'transaction.kind.income',
  TRANSFER: 'transaction.kind.transfer',
} as const

type Row = readonly [label: string, value: string | undefined]

const action =
  'flex min-h-12 items-center justify-center rounded-field font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** A saved transaction in full, with Editar and Excluir, which need the connection (ADR-0007). */
export function TransactionDetailSheet({
  open,
  onOpenChange,
  item,
  categories,
  accounts,
  workspace,
  displayName,
  members,
  online,
  month,
  onDelete,
}: TransactionDetailSheetProps) {
  const intl = useIntl()
  const text = (id: MessageId) => intl.formatMessage({ id })
  const [confirming, setConfirming] = useState(false)
  const category = item.categoryId ? categories.get(item.categoryId) : undefined
  const cents = parseCents(item.amount.amount)
  const transfer = item.kind === 'TRANSFER'
  const amount =
    item.kind === 'INCOME'
      ? `+${formatBRL(cents)}`
      : transfer
        ? formatBRL(cents)
        : formatBRL(-cents)
  const date = intl.formatDate(`${item.occurredOn}T00:00:00Z`, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  })
  const by =
    item.memberId === workspace.membershipId
      ? `${text('transactions.detail.byMe')} (${firstName(displayName)})`
      : (memberLabel(members?.get(item.memberId), intl) ?? text('transactions.detail.byOther'))
  // A Youth does not see the family's accounts: the API leaves them out, and so does the list.
  const unseen =
    workspace.role === 'YOUTH'
      ? intl.formatMessage({ id: 'transactions.familyAccount' })
      : undefined
  const accountName = (id: string) => accounts.get(id)?.name ?? unseen
  const where: Row[] = transfer
    ? [
        [text('transactions.detail.from'), accountName(item.accountId)],
        [
          text('transactions.detail.to'),
          item.toAccountId ? accountName(item.toAccountId) : undefined,
        ],
      ]
    : [
        [text('transactions.detail.account'), accountName(item.accountId)],
        [text('transactions.detail.category'), category ? categoryLabel(category) : undefined],
      ]
  const rows: Row[] = [
    [text('transactions.detail.kind'), text(KIND[item.kind])],
    [text('transactions.detail.amount'), amount],
    [text('transactions.detail.date'), date],
    ...where,
    [text('transactions.detail.description'), item.description ?? undefined],
    [text('transactions.detail.by'), by],
  ]
  const title = transfer
    ? `↔ ${text('transactions.transfer')}`
    : category
      ? categoryLabel(category)
      : text('transactions.uncategorized')

  return (
    <>
      <Sheet open={open && !confirming} onOpenChange={onOpenChange} title={title}>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
          {rows
            .filter(([, value]) => value)
            .map(([label, value]) => (
              <div key={label} className="contents">
                <dt className="text-text-muted">{label}</dt>
                <dd className="text-right">{value}</dd>
              </div>
            ))}
        </dl>
        {!online && (
          <p className="mt-4 text-text-muted">
            <FormattedMessage id="transactions.offlineEditing" />
          </p>
        )}
        <div className="mt-6 flex flex-col gap-2">
          {online ? (
            <Link
              to={`/lancar?editar=${item.id}&mes=${month}`}
              className={`${action} bg-primary text-on-primary`}
            >
              <FormattedMessage id="transactions.edit" />
            </Link>
          ) : (
            <button type="button" disabled className={`${action} bg-primary text-on-primary`}>
              <FormattedMessage id="transactions.edit" />
            </button>
          )}
          <button
            type="button"
            disabled={!online}
            onClick={() => setConfirming(true)}
            className={`${action} border border-negative text-negative`}
          >
            <FormattedMessage id="transactions.delete" />
          </button>
        </div>
      </Sheet>
      <ConfirmDialog
        open={open && confirming}
        title={<FormattedMessage id="transactions.deleteConfirm.title" />}
        body={<FormattedMessage id="transactions.deleteConfirm.body" />}
        confirmLabel={<FormattedMessage id="transactions.delete" />}
        cancelLabel={<FormattedMessage id="transactions.discardNo" />}
        onConfirm={() => {
          setConfirming(false)
          onOpenChange(false)
          onDelete(item.id)
        }}
        onCancel={() => setConfirming(false)}
      />
    </>
  )
}

type MessageId = keyof typeof messages
