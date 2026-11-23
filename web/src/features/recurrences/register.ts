import { registerMessages } from '@/shared/i18n'
import { registerModule } from '@/shared/modules'
import { recurrencesMessages } from './messages.pt-BR'

// Imported for its effect: by the app's routes, so the planning module is on offer, and by every
// recurrences module that shows text.
registerMessages(recurrencesMessages)
registerModule({ key: 'planning', available: true })
