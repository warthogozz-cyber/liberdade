import { useId, useState } from 'react'
import { useIntl } from 'react-intl'
import { memberName } from '@/features/members/memberName'
import { useSetAccountOwner } from '@/shared/api/detail'
import { useMembers } from '@/shared/api/family'
import { useToast } from '@/shared/ui'
import type { AccountView, MeWorkspace } from '@/shared/api'
import './register'

interface OwnerPickerProps {
  workspace: MeWorkspace
  /**
   * An account that exists: choosing saves right away, since the owner has its own call. Without
   * one the choice is only reported to `onChange`, for the account about to be created.
   */
  account?: AccountView
  value?: string | null
  onChange?: (membershipId: string | null) => void
  disabled?: boolean
}

const option =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field border border-text-muted/30 px-3 has-[:checked]:border-primary has-[:checked]:bg-primary/10 has-[:disabled]:opacity-50'

/** Who an account belongs to ("Titular"): one of the people in the family, or "Ninguém". */
export function OwnerPicker({ workspace, account, value, onChange, disabled }: OwnerPickerProps) {
  const intl = useIntl()
  const toast = useToast()
  const ids = useId()
  const members = (useMembers(workspace.id).data ?? []).filter((each) => each.status === 'ACTIVE')
  const setOwner = useSetAccountOwner(workspace.id)
  const [saved, setSaved] = useState<string | null>(account?.ownerMembershipId ?? null)
  const current = account ? saved : (value ?? null)

  function choose(membershipId: string | null) {
    if (!account) {
      onChange?.(membershipId)
      return
    }
    const before = saved
    setSaved(membershipId)
    setOwner.mutate(
      { id: account.id, membershipId },
      {
        onSuccess: () => toast.show({ message: intl.formatMessage({ id: 'accounts.saved' }) }),
        onError: (error) => {
          console.error(error)
          setSaved(before)
          toast.show({ message: intl.formatMessage({ id: 'common.actionFailed' }) })
        },
      },
    )
  }

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 font-medium">
        {intl.formatMessage({ id: 'accounts.field.owner' })}
      </legend>
      <div className="grid grid-cols-2 gap-2">
        <label className={option}>
          <input
            type="radio"
            name={`${ids}-owner`}
            disabled={disabled}
            checked={current === null}
            onChange={() => choose(null)}
          />
          {intl.formatMessage({ id: 'accounts.owner.none' })}
        </label>
        {members.map((member) => (
          <label key={member.id} className={option}>
            <input
              type="radio"
              name={`${ids}-owner`}
              disabled={disabled}
              checked={current === member.id}
              onChange={() => choose(member.id)}
            />
            {memberName(member, intl)}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
