import { registerMessages } from '@/shared/i18n'
import { categoriesMessages } from './messages.pt-BR'

// Imported for its effect by every categories module that shows text, so the page, its sheet and
// their tests all find the ids once the module is loaded.
registerMessages(categoriesMessages)
