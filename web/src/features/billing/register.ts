import { registerMessages } from '@/shared/i18n'
import { billingMessages } from './messages.pt-BR'

// Imported for its effect by the Plan screen, its sheet and their tests.
registerMessages(billingMessages)
