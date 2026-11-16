import type { messages } from './messages.pt-BR'

declare global {
  namespace FormatjsIntl {
    interface Message {
      ids: keyof typeof messages
    }
  }
}
