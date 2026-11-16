import { registerMessages } from '@/shared/i18n'
import { dashboardMessages } from './messages.pt-BR'

// Imported for its effect by the dashboard page, so it and its tests find the ids once the module
// is loaded.
registerMessages(dashboardMessages)
