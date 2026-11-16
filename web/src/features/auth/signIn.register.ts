import { registerMessages } from '@/shared/i18n'
import { signInMessages } from './signIn.messages.pt-BR'

// Imported for its effect by the page, so the page and its tests find the ids once it is loaded.
registerMessages(signInMessages)
