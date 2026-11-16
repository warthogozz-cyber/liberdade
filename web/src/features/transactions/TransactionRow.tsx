import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import type { AccountView, CategoryView, MeWorkspace } from '@/shared/api'
import { firstName } from '@/shared/i18n'
import { formatBRL, parseCents } from '@/shared/money'
import { discardOutboxItem } from '@/shared/offline'
import { ConfirmDialog, Sheet, useToast } from '@/shared/ui'
import { categoryLabel, transferRoute, type ListItem } from './listItems'
import { TransactionDetailSheet } from './TransactionDetailSheet'

interface TransactionRowProps {
  item: ListItem
  categories: Map<string, CategoryView>
  accounts: Map<string, AccountView>
  workspace: MeWorkspace
  displayName: string
  /** The month of the list, which the detail's Editar sends back to. */
  month: string
  online: boolean
  onDelete: (id: string) => void
}

/** A transaction of the list; a queued one says it waits for the signal or needs attention. */
export function TransactionRow({
  item,
  categories,
  accounts,
  workspace,
  displayName,
  month,
  online,
  onDelete,
}: TransactionRowProps) {
  const intl = useIntl()
  const toast = useToast()
  const [step, setStep] = useState<'closed' | 'sheet' | 'confirm' | 'detail'>('closed')
  const category = item.categoryId ? categories.get(item.categoryId) : undefined
  const cents = parseCents(item.amount.amount)
  const income = item.kind === 'INCOME'
  const transfer = item.kind === 'TRANSFER'
  const mine = item.outbox !== undefined || item.memberId === workspace.membershipId
  const details = transfer
    ? transferRoute(item, accounts)
    : [accounts.get(item.accountId)?.name, mine ? firstName(displayName) : undefined]
        .filter(Boolean)
        .join(' · ')
  const outbox = item.outbox
  const saved = outbox === undefined

  const summary = (
    <>
      <div className="flex w-full items-baseline justify-between gap-4">
        <span className="font-medium">
          {transfer ? (
            `↔ ${intl.formatMessage({ id: 'transactions.transfer' })}`
          ) : category ? (
            categoryLabel(category)
          ) : (
            <FormattedMessage id="transactions.uncategorized" />
          )}
        </span>
        <span className={`font-semibold tabular-nums ${income ? 'text-positive' : ''}`}>
          {income ? `+${formatBRL(cents)}` : transfer ? formatBRL(cents) : formatBRL(-cents)}
        </span>
      </div>
      <div className="flex w-full items-center justify-between gap-4 text-sm text-text-muted">
        <span>{details}</span>
        {outbox?.status === 'pending' && (
          <span className="rounded-field bg-bg px-2 py-0.5">
            <FormattedMessage id="transactions.pending" />
          </span>
        )}
        {outbox?.status === 'needs-attention' && (
          <button
            type="button"
            onClick={() => setStep('sheet')}
            className="min-h-12 rounded-field px-2 font-medium text-negative underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="transactions.needsAttention" />
          </button>
        )}
      </div>
    </>
  )

  return (
    <li className="flex flex-col gap-1 py-3">
      {saved ? (
        <button
          type="button"
          onClick={() => setStep('detail')}
          className="flex min-h-12 w-full flex-col gap-1 rounded-field text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {summary}
        </button>
      ) : (
        summary
      )}
      {outbox?.status === 'needs-attention' && (
        <>
          <Sheet
            open={step === 'sheet'}
            onOpenChange={(open) => setStep(open ? 'sheet' : 'closed')}
            title={intl.formatMessage({ id: 'transactions.attentionTitle' })}
          >
            <p>
              {outbox.problem?.detail ?? <FormattedMessage id="transactions.attentionFallback" />}
            </p>
            <div className="mt-6 flex flex-col gap-2">
              <Link
                to={`/lancar?corrigir=${item.id}`}
                className="flex min-h-12 items-center justify-center rounded-field bg-primary font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <FormattedMessage id="transactions.fix" />
              </Link>
              <button
                type="button"
                onClick={() => setStep('confirm')}
                className="min-h-12 rounded-field border border-negative font-medium text-negative focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <FormattedMessage id="transactions.discard" />
              </button>
            </div>
          </Sheet>
          <ConfirmDialog
            open={step === 'confirm'}
            title={<FormattedMessage id="transactions.discardTitle" />}
            body={<FormattedMessage id="transactions.discardBody" />}
            confirmLabel={<FormattedMessage id="transactions.discard" />}
            cancelLabel={<FormattedMessage id="transactions.discardNo" />}
            onConfirm={() => {
              setStep('closed')
              discardOutboxItem(item.id).catch((error: unknown) => {
                console.error(error)
                toast.show({ message: intl.formatMessage({ id: 'common.actionFailed' }) })
              })
            }}
            onCancel={() => setStep('closed')}
          />
        </>
      )}
      {saved && (
        <TransactionDetailSheet
          open={step === 'detail'}
          onOpenChange={(open) => setStep(open ? 'detail' : 'closed')}
          item={item}
          categories={categories}
          accounts={accounts}
          workspace={workspace}
          displayName={displayName}
          online={online}
          month={month}
          onDelete={onDelete}
        />
      )}
    </li>
  )
}
