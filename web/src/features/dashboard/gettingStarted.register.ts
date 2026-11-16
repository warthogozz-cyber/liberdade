import { registerMessages } from '@/shared/i18n'
import { gettingStartedMessages } from './gettingStarted.messages.pt-BR'

// Imported for its effect by the card, so the card and its tests find the ids once the module is
// loaded.
registerMessages(gettingStartedMessages)
