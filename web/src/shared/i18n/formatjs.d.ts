// The type-only import is the app's one deliberate link from shared code to a route's ids.
// eslint-disable-next-line no-restricted-imports
import type { accountsMessages } from '@/features/accounts/messages.pt-BR'
import type { messages } from './messages.pt-BR'

// Each lazy route's bundle adds its ids here, by type only: nothing of it is imported at runtime.
declare global {
  namespace FormatjsIntl {
    interface Message {
      ids: keyof typeof messages | keyof typeof accountsMessages
    }
  }
}
