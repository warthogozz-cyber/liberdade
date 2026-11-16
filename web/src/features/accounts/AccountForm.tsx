import { useId, useState, type FormEvent } from 'react'
import { useIntl } from 'react-intl'
import {
  ApiError,
  useCreateAccount,
  useUpdateAccount,
  type AccountType,
  type AccountView,
  type MeWorkspace,
  type UpdateAccountRequest,
} from '@/shared/api'
import { centsToApi } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { ACCOUNT_TYPES, accountTypeInfo } from './accountTypes'
import { BalanceInput } from './BalanceInput'
import { OwnerPicker } from './OwnerPicker'
import { signedCents } from './signedCents'
import './register'

const MAX_NAME_LENGTH = 60

interface AccountFormProps {
  workspace: MeWorkspace
  /** The account being edited: its name and type. Without one, the form creates an account. */
  account?: AccountView
  /** Called after a save, once the notice is shown. */
  onDone: () => void
}

/** Creates an account (with its opening balance) or edits its name and type. */
export function AccountForm({ workspace, account, onDone }: AccountFormProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const create = useCreateAccount(workspace.id)
  const update = useUpdateAccount(workspace.id)
  const [name, setName] = useState(account?.name ?? '')
  const [type, setType] = useState<AccountType>(account?.type ?? 'CHECKING')
  const [cents, setCents] = useState(0n)
  const [negative, setNegative] = useState(false)
  const [ownerId, setOwnerId] = useState<string | null>(null)
  const [nameError, setNameError] = useState<string>()
  const [error, setError] = useState<string>()
  const ids = useId()
  const nameErrorId = `${ids}-name-error`
  const hintId = `${ids}-hint`
  const typeLegendId = `${ids}-type-legend`
  // Only an Owner or an Adult chooses who an account belongs to; the API still answers for it.
  const canChooseOwner = workspace.role === 'OWNER' || workspace.role === 'ADULT'
  const pending = create.isPending || update.isPending

  const trimmed = name.trim()
  // Only what changed is sent (partial PATCH).
  const changes: UpdateAccountRequest = {
    ...(account && trimmed !== account.name && { name: trimmed }),
    ...(account && type !== account.type && { type }),
  }
  const changed = Object.keys(changes).length > 0
  const canSave = online && !pending && trimmed !== '' && (!account || changed)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!canSave) return
    setNameError(undefined)
    setError(undefined)
    try {
      if (account) {
        await update.mutateAsync({ id: account.id, body: changes })
      } else {
        await create.mutateAsync({
          name: trimmed,
          type,
          initialBalance: {
            amount: centsToApi(signedCents(cents, negative)),
            currency: workspace.currency,
          },
          ...(ownerId && { ownerMembershipId: ownerId }),
        })
      }
    } catch (failure) {
      const detail =
        (failure instanceof ApiError && failure.detail) ||
        intl.formatMessage({ id: 'common.actionFailed' })
      if (failure instanceof ApiError && failure.code === 'account.name-taken') {
        setNameError(detail)
      } else {
        setError(detail)
      }
      return
    }
    toast.show({
      message: intl.formatMessage({ id: account ? 'accounts.saved' : 'accounts.created' }),
    })
    onDone()
  }

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'accounts.field.name' })}</span>
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
      <fieldset
        role="radiogroup"
        className="flex flex-col gap-2"
        aria-labelledby={typeLegendId}
        aria-describedby={hintId}
      >
        <legend id={typeLegendId} className="mb-1 font-medium">
          {intl.formatMessage({ id: 'accounts.field.type' })}
        </legend>
        <div className="grid grid-cols-2 gap-2">
          {ACCOUNT_TYPES.map((info) => (
            <label
              key={info.type}
              className="flex min-h-12 items-center gap-2 rounded-field border border-text-muted/30 px-3 has-[:checked]:border-primary has-[:checked]:bg-primary/10"
            >
              <input
                type="radio"
                name={`${ids}-type`}
                checked={type === info.type}
                onChange={() => setType(info.type)}
              />
              <span aria-hidden="true">{info.icon}</span>
              {intl.formatMessage({ id: info.labelId })}
            </label>
          ))}
        </div>
        <p id={hintId} className="text-text-muted">
          {intl.formatMessage({ id: accountTypeInfo(type).hintId })}
        </p>
      </fieldset>
      {canChooseOwner && (
        <OwnerPicker
          workspace={workspace}
          account={account}
          value={ownerId}
          onChange={setOwnerId}
        />
      )}
      {!account && (
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 font-medium">
            {intl.formatMessage({ id: 'accounts.field.initialBalance' })}
          </legend>
          <BalanceInput
            label={intl.formatMessage({ id: 'accounts.field.initialBalance' })}
            cents={cents}
            negative={negative}
            onCents={setCents}
            onNegative={setNegative}
          />
        </fieldset>
      )}
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      {!online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'accounts.offline' })}</p>
      )}
      <button
        type="submit"
        disabled={!canSave}
        className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        {intl.formatMessage({ id: 'accounts.save' })}
      </button>
    </form>
  )
}
