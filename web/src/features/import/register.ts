import { registerMessages } from '@/shared/i18n'
import { importMessages } from './messages.pt-BR'

// Imported for its effect by the lazy import modules that show these ids.
registerMessages(importMessages)
