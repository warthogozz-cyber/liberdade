import { lazy } from 'react'
import { registerMessages } from '@/shared/i18n'
import { registerModule, registerSlot } from '@/shared/modules'
import { planningMessages } from './messages.pt-BR'

// Imported for its effect: by the app's routes, so the planning module is on offer, and by every
// planning screen that shows text. Screens enter only through `lazy`, to stay out of the first load.
registerMessages(planningMessages)
registerModule({ key: 'planning', available: true })

registerSlot('workspace', {
  key: 'planning-invalidator',
  module: 'planning',
  roles: ['OWNER', 'ADULT', 'VIEWER', 'YOUTH'],
  Component: lazy(() => import('./PlanningInvalidator')),
})

registerSlot('workspace', {
  key: 'planning-budget-toast',
  module: 'planning',
  roles: ['OWNER', 'ADULT'],
  Component: lazy(() => import('./BudgetToastWatcher')),
})

registerSlot('home', {
  key: 'planning-budget-card',
  module: 'planning',
  roles: ['OWNER', 'ADULT', 'VIEWER'],
  Component: lazy(() => import('./BudgetHomeCard')),
})
