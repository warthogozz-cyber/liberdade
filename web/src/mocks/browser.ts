import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/** The mock API for `pnpm dev:mocks`; `src/main.tsx` starts it before the first render. */
export const worker = setupWorker(...handlers)
