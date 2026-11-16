import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** The mock API for Vitest; `src/test-setup.ts` starts it and resets it after each test. */
export const server = setupServer(...handlers)
