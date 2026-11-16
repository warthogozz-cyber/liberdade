import { registerMessages } from '@/shared/i18n'
import { membersMessages } from './messages.pt-BR'

// Imported for its effect by every family module that shows text, so the screens, their sheets and
// their tests all find the ids once the module is loaded.
registerMessages(membersMessages)
