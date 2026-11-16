import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { postJson, queryKeys, type MeView } from '@/shared/api'
import { clearSession, db, type OutboxItem } from '@/shared/offline'
import { createTestQueryClient } from '@/shared/testing'

const userId = '0192f3a4-0000-7000-8000-000000000001'

const me: MeView = {
  user: { id: userId, displayName: 'Ana Teste', passkeyCreatedAt: null },
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
})
