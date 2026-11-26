import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { postJson, queryKeys, type MeView } from '@/shared/api'
import {
  clearSession,
  INVITATION_TOKEN_KEY,
  PENDING_PASSKEY_KEY,
  REAUTH_USER_KEY,
  RETURN_PATH_KEY,
  type OutboxItem,
} from '@/shared/offline'
import { db } from '@/shared/offline/db'
import { createTestQueryClient } from '@/shared/testing'

const userId = '0192f3a4-0000-7000-8000-000000000001'

const me: MeView = {
  user: {
    id: userId,
    displayName: 'Ana Teste',
    passkeyCreatedAt: null,
    deletionScheduledFor: null,
  },
  workspaces: [],
  suggestedWorkspaceName: 'Família Teste',
}

const item: OutboxItem = {
  id: '0192f3a4-5b6c-7d8e-9f01-23456789abcd',
  userId,
  workspaceId: '0192f3a4-0000-7000-8000-000000000002',
  type: 'transaction.create',
  payload: {
    id: '0192f3a4-5b6c-7d8e-9f01-23456789abcd',
    kind: 'EXPENSE',
    amount: { amount: '12.50', currency: 'BRL' },
    occurredOn: '2026-11-03',
    accountId: '0192f3a4-0000-7000-8000-000000000003',
    categoryId: '0192f3a4-0000-7000-8000-000000000004',
  },
  createdAt: 1_793_700_000_000,
  sendAfter: 1_793_700_000_000,
  attempts: 0,
  status: 'pending',
}

describe('clearSession', () => {
  it('empties the cache, the saved cache and the CSRF token, and keeps the outbox', async () => {
    const tokenRequests: Request[] = []
    const record = ({ request }: { request: Request }) => {
      if (new URL(request.url).pathname === '/v1/csrf') tokenRequests.push(request)
    }
    server.events.on('request:start', record)
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.me, me)
    await db.kv.put({ key: 'liberdade-query-cache', value: '{}' })
    await db.outbox.add(item)
    await postJson('/logout')
    expect(tokenRequests).toHaveLength(1)

    await clearSession(queryClient)

    expect(queryClient.getQueryData(queryKeys.me)).toBeUndefined()
    expect(await db.kv.count()).toBe(0)
    expect(await db.outbox.count()).toBe(1)
    await postJson('/logout')
    expect(tokenRequests).toHaveLength(2)
    server.events.removeListener('request:start', record)
  })

  it("forgets what this tab kept for the session, the invitation's token included", async () => {
    const keys = [RETURN_PATH_KEY, REAUTH_USER_KEY, PENDING_PASSKEY_KEY, INVITATION_TOKEN_KEY]
    for (const key of keys) sessionStorage.setItem(key, 'kept')
    sessionStorage.setItem('liberdade.other', 'kept')

    await clearSession(createTestQueryClient())

    for (const key of keys) expect(sessionStorage.getItem(key)).toBeNull()
    expect(sessionStorage.getItem('liberdade.other')).toBe('kept')
    sessionStorage.removeItem('liberdade.other')
  })

  it("forgets the Feira's saved location choice of every person, and only that", async () => {
    localStorage.setItem('liberdade.markets.user-a', '{"mode":"precise"}')
    localStorage.setItem('liberdade.markets.user-b', '{"mode":"home"}')
    localStorage.setItem('liberdade.tips', 'on')

    await clearSession(createTestQueryClient())

    expect(localStorage.getItem('liberdade.markets.user-a')).toBeNull()
    expect(localStorage.getItem('liberdade.markets.user-b')).toBeNull()
    expect(localStorage.getItem('liberdade.tips')).toBe('on')
    localStorage.clear()
  })
})
