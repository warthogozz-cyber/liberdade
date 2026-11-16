import { registerMessages } from '@/shared/i18n'
import { transactionsMoreMessages } from './more.messages.pt-BR'

// Imported for its effect by the lazy transaction modules that show these ids.
registerMessages(transactionsMoreMessages)
