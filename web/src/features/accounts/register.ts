import { registerMessages } from '@/shared/i18n'
import { accountsMessages } from './messages.pt-BR'

// Imported for its effect by every accounts module that shows text, so the page, its sheets and
// their tests all find the ids once the module is loaded.
registerMessages(accountsMessages)
