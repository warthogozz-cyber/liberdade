import { registerMessages } from '@/shared/i18n'
import { legalLabelsMessages } from './labels.messages.pt-BR'

// Imported for its effect by every screen that shows a legal link or the acceptance, so the screen
// and its tests find the ids once the module is loaded.
registerMessages(legalLabelsMessages)
