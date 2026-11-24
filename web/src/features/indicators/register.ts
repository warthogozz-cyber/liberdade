import { registerModule } from '@/shared/modules'

// Imported for its effect by the app's routes, so the module is on offer. It stays this small on
// purpose: anything it imports lands in the first load. The screens register their own text.
registerModule({ key: 'indicators', available: true })
