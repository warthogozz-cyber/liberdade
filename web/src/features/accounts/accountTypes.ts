import type { AccountType } from '@/shared/api'

type MessageId = FormatjsIntl.Message['ids']

interface AccountTypeInfo {
  type: AccountType
  labelId: MessageId
  hintId: MessageId
  icon: string
}

/** The kinds of account, in the order they are offered. */
export const ACCOUNT_TYPES = [
  {
    type: 'CHECKING',
    labelId: 'accounts.type.CHECKING',
    hintId: 'accounts.hint.CHECKING',
    icon: '🏦',
  },
  {
    type: 'SAVINGS',
    labelId: 'accounts.type.SAVINGS',
    hintId: 'accounts.hint.SAVINGS',
    icon: '🐷',
  },
  { type: 'CASH', labelId: 'accounts.type.CASH', hintId: 'accounts.hint.CASH', icon: '💵' },
  {
    type: 'CREDIT_CARD',
    labelId: 'accounts.type.CREDIT_CARD',
    hintId: 'accounts.hint.CREDIT_CARD',
    icon: '💳',
  },
  {
    type: 'WALLET',
    labelId: 'accounts.type.WALLET',
    hintId: 'accounts.hint.WALLET',
    icon: '📱',
  },
  {
    type: 'INVESTMENT',
    labelId: 'accounts.type.INVESTMENT',
    hintId: 'accounts.hint.INVESTMENT',
    icon: '📈',
  },
] as const satisfies readonly AccountTypeInfo[]

const BY_TYPE = new Map<AccountType, AccountTypeInfo>(
  ACCOUNT_TYPES.map((info) => [info.type, info]),
)

/** What to show for an account type. */
export function accountTypeInfo(type: AccountType): AccountTypeInfo {
  return BY_TYPE.get(type) ?? ACCOUNT_TYPES[0]
}
