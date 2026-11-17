import { registerMessages } from '@/shared/i18n'
import { legalMessages } from './messages.pt-BR'
import './labels.register'

// Imported for its effect by the policy and the terms, so they and their tests find the ids.
registerMessages(legalMessages)
