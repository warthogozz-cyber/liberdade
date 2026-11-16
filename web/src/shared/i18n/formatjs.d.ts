// The type-only import is the app's one deliberate link from shared code to a route's ids.
// eslint-disable-next-line no-restricted-imports
import type { accountsMessages } from '@/features/accounts/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { categoriesMessages } from '@/features/categories/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { membersMessages } from '@/features/members/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { dashboardMessages } from '@/features/dashboard/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { signInMessages } from '@/features/auth/signIn.messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { gettingStartedMessages } from '@/features/dashboard/gettingStarted.messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { transactionsMoreMessages } from '@/features/transactions/more.messages.pt-BR'
import type { reauthMessages } from '@/shared/auth/messages.pt-BR'
import type { messages } from './messages.pt-BR'

// Each lazy route's bundle adds its ids here, by type only: nothing of it is imported at runtime.
declare global {
  namespace FormatjsIntl {
    interface Message {
      ids:
        | keyof typeof messages
        | keyof typeof accountsMessages
        | keyof typeof categoriesMessages
        | keyof typeof dashboardMessages
        | keyof typeof gettingStartedMessages
        | keyof typeof membersMessages
        | keyof typeof reauthMessages
        | keyof typeof signInMessages
        | keyof typeof transactionsMoreMessages
    }
  }
}
