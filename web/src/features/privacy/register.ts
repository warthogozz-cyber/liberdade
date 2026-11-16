import { registerMessages } from '@/shared/i18n'
import { privacyMessages } from './messages.pt-BR'

// Imported for its effect by every privacy screen, so the screens, their sheets and their tests
// all find the ids once the module is loaded.
registerMessages(privacyMessages)
