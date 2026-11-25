// The fake IndexedDB loads before anything opens the Dexie `db`.
import 'fake-indexeddb/auto'
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { resetMockData } from '@/mocks/data'
import { server } from '@/mocks/server'
import { resetCsrf } from '@/shared/api'
import { db } from '@/shared/offline'

// MSW 3 renamed `onUnhandledRequest` to `onUnhandledFrame`: a call no handler answers fails the test.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))

// The first visit tips stay off so the screen tests keep their layout; the tip tests turn them on.
beforeEach(() => {
  if (typeof window !== 'undefined') localStorage.setItem('liberdade.tips', 'off')
})

afterEach(async () => {
  server.resetHandlers()
  resetMockData()
  resetCsrf()
  // The ESLint tests run in Node, without a window.
  if (typeof window !== 'undefined') {
    localStorage.clear()
    sessionStorage.clear()
  }
  await Promise.all([db.outbox.clear(), db.kv.clear()])
})

afterAll(() => server.close())
