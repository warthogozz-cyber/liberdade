// The type-only import is the app's one deliberate link from shared code to a route's ids.
// eslint-disable-next-line no-restricted-imports
import type { accountsMessages } from '@/features/accounts/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { billingMessages } from '@/features/billing/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { categoriesMessages } from '@/features/categories/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { gamificationMessages } from '@/features/gamification/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { legalLabelsMessages } from '@/features/legal/labels.messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { legalMessages } from '@/features/legal/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { membersMessages } from '@/features/members/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { modulesMessages } from '@/features/modules/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { privacyMessages } from '@/features/privacy/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { dashboardMessages } from '@/features/dashboard/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { signInMessages } from '@/features/auth/signIn.messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { gettingStartedMessages } from '@/features/dashboard/gettingStarted.messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { transactionsMoreMessages } from '@/features/transactions/more.messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { portraitMessages } from '@/features/portrait/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { settingsMessages } from '@/features/settings/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { importMessages } from '@/features/import/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { indicatorsMessages } from '@/features/indicators/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { planningMessages } from '@/features/planning/messages.pt-BR'
// eslint-disable-next-line no-restricted-imports
import type { recurrencesMessages } from '@/features/recurrences/messages.pt-BR'
import type { reauthMessages } from '@/shared/auth/messages.pt-BR'
import type { sourceStampMessages } from '@/shared/ui/sourceStamp.messages.pt-BR'
import type { messages } from './messages.pt-BR'

// Each lazy route's bundle adds its ids here, by type only: nothing of it is imported at runtime.
declare global {
  namespace FormatjsIntl {
    interface Message {
      ids:
        | keyof typeof messages
        | keyof typeof accountsMessages
        | keyof typeof billingMessages
        | keyof typeof categoriesMessages
        | keyof typeof dashboardMessages
        | keyof typeof gamificationMessages
        | keyof typeof gettingStartedMessages
        | keyof typeof importMessages
        | keyof typeof indicatorsMessages
        | keyof typeof legalLabelsMessages
        | keyof typeof legalMessages
        | keyof typeof membersMessages
        | keyof typeof modulesMessages
        | keyof typeof planningMessages
        | keyof typeof portraitMessages
        | keyof typeof privacyMessages
        | keyof typeof settingsMessages
        | keyof typeof recurrencesMessages
        | keyof typeof reauthMessages
        | keyof typeof sourceStampMessages
        | keyof typeof signInMessages
        | keyof typeof transactionsMoreMessages
    }
  }
}
