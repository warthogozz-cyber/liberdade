import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, useUpdateAccount, type AccountView, type MeWorkspace } from '@/shared/api'
import { centsToApi } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { Sheet } from '@/shared/ui/Sheet'
import { BalanceInput } from './BalanceInput'
import { signedCents } from './signedCents'
import './register'

interface AdjustBalanceSheetProps {
  workspace: MeWorkspace
  account: AccountView
  onClose: () => void
}

/** "What is the balance today?": sets the account's balance, however it got there. */
export function AdjustBalanceSheet({ workspace, account, onClose }: AdjustBalanceSheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const update = useUpdateAccount(workspace.id)
  const [cents, setCents] = useState(0n)
  const [negative, setNegative] = useState(false)
  const [error, setError] = useState<string>()

  async function save() {
    if (!online || update.isPending) return
    setError(undefined)
    try {
      await update.mutateAsync({
        id: account.id,
        body: {
          balance: {
            amount: centsToApi(signedCents(cents, negative)),
            currency: workspace.currency,
          },
        },
      })
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'accounts.adjusted' }) })
    onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'accounts.adjust' })}
    >
      <div className="flex flex-col gap-4">
        <p className="font-medium">{intl.formatMessage({ id: 'accounts.adjust.question' })}</p>
        <BalanceInput
          label={intl.formatMessage({ id: 'accounts.field.initialBalance' })}
          cents={cents}
          negative={negative}
          onCents={setCents}
          onNegative={setNegative}
        />
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'accounts.offline' })}</p>
        )}
        <button
          type="button"
          disabled={!online || update.isPending}
          onClick={() => void save()}
          className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
        >
          {intl.formatMessage({ id: 'accounts.save' })}
        </button>
      </div>
    </Sheet>
  )
}
