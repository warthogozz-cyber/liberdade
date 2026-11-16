import { act, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { createFamily, mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import {
  API_URL,
  ApiError,
  postTransaction,
  prefetchWorkspace,
  useAccounts,
  useCategories,
  useCreateWorkspace,
  useCurrentWorkspace,
  selectWorkspace,
  useDashboard,
  useMe,
  queryKeys,
  useTransactions,
  useWorkspaces,
} from '@/shared/api'
import { createTestQueryClient, renderHookWithProviders } from '@/shared/testing'

describe('useMe', () => {
  it('reads the signed-in user, still without a family', async () => {
    const { result } = renderHookWithProviders(() => useMe())

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toMatchObject({
      user: { displayName: 'Ana Teste' },
      workspaces: [],
      suggestedWorkspaceName: 'Família Teste',
    })
  })
})

describe('useCurrentWorkspace', () => {
  it('is undefined without a family and the family once there is one', async () => {
    const empty = renderHookWithProviders(() => useCurrentWorkspace())
    await waitFor(() => expect(empty.queryClient.getQueryState(['me'])?.status).toBe('success'))
    expect(empty.result.current).toBeUndefined()

    seedMockFamily()
    const { result } = renderHookWithProviders(() => useCurrentWorkspace())

    await waitFor(() => expect(result.current?.name).toBe('Família Teste'))
    expect(result.current).toMatchObject({ role: 'OWNER', currency: 'BRL' })
  })
})

describe('selected family', () => {
  function twoFamilies() {
    return [
      createFamily('Família Teste', 'America/Fortaleza'),
      createFamily('Casa Teste', 'America/Sao_Paulo'),
    ]
  }

  it('selectedWorkspaceWins', async () => {
    const [, second] = twoFamilies()
    localStorage.setItem('liberdade.workspaceId', second.id)
    const { result } = renderHookWithProviders(() => ({
      current: useCurrentWorkspace(),
      all: useWorkspaces(),
    }))

    await waitFor(() => expect(result.current.all).toHaveLength(2))
    expect(result.current.current?.id).toBe(second.id)
  })

  it('fallsBackToFirst', async () => {
    const [first] = twoFamilies()
    localStorage.setItem('liberdade.workspaceId', 'not-a-family-of-mine')
    const { result } = renderHookWithProviders(() => useCurrentWorkspace())

    await waitFor(() => expect(result.current?.id).toBe(first.id))
  })

  it('switches at once on selectWorkspace and when another tab chooses', async () => {
    const [first, second] = twoFamilies()
    const { result } = renderHookWithProviders(() => useCurrentWorkspace())
    await waitFor(() => expect(result.current?.id).toBe(first.id))

    act(() => selectWorkspace(second.id))
    expect(result.current?.id).toBe(second.id)
    expect(localStorage.getItem('liberdade.workspaceId')).toBe(second.id)

    act(() => {
      localStorage.setItem('liberdade.workspaceId', first.id)
      window.dispatchEvent(new StorageEvent('storage', { key: 'liberdade.workspaceId' }))
      window.dispatchEvent(new StorageEvent('storage', { key: 'other' }))
    })
    expect(result.current?.id).toBe(first.id)
  })

  it('keeps the cache of the other family', async () => {
    const [first, second] = twoFamilies()
    const { result, queryClient } = renderHookWithProviders(() => useCurrentWorkspace())
    await waitFor(() => expect(result.current?.id).toBe(first.id))
    queryClient.setQueryData(queryKeys.accounts(first.id), ['kept'])

    act(() => selectWorkspace(second.id))

    expect(queryClient.getQueryData(queryKeys.accounts(first.id))).toEqual(['kept'])
  })
})

describe('useCreateWorkspace', () => {
  it('sends the browser time zone, then useMe shows the family', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces`, async ({ request }) => {
        bodies.push(await request.clone().json())
      }),
    )
    const { result, queryClient } = renderHookWithProviders(() => ({
      me: useMe(),
      create: useCreateWorkspace(),
    }))
    await waitFor(() => expect(result.current.me.isSuccess).toBe(true))

    await act(() => result.current.create.mutateAsync('Família Teste'))

    expect(bodies).toEqual([
      { name: 'Família Teste', timezone: Intl.DateTimeFormat().resolvedOptions().timeZone },
    ])
    // The mutation resolves only once `/v1/me` is fetched again.
    expect(queryClient.getQueryData(queryKeys.me)).toMatchObject({
      workspaces: [{ name: 'Família Teste', role: 'OWNER' }],
    })
    await waitFor(() =>
      expect(result.current.me.data?.workspaces).toMatchObject([
        { name: 'Família Teste', role: 'OWNER' },
      ]),
    )
  })

  it('resolves both of two creations in a row, leaving one family', async () => {
    const { result } = renderHookWithProviders(() => useCreateWorkspace())

    await act(() =>
      Promise.all([
        result.current.mutateAsync('Família Teste'),
        result.current.mutateAsync('Família Teste'),
      ]),
    )

    expect(mockState.workspaces).toHaveLength(1)
  })

  it('rejects with the ApiError of a refused name', async () => {
    const { result } = renderHookWithProviders(() => useCreateWorkspace())

    let error: unknown
    await act(async () => {
      error = await result.current.mutateAsync('   ').catch((caught: unknown) => caught)
    })

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 422, code: 'request.invalid' })
  })
})

describe('family hooks', () => {
  it('lists the 13 expense categories, Mercado first', async () => {
    const workspace = seedMockFamily()
    const { result } = renderHookWithProviders(() => useCategories(workspace.id, 'EXPENSE'))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toHaveLength(13)
    expect(result.current.data?.[0]).toMatchObject({ name: 'Mercado', icon: '🛒' })
  })

  it('lists the wallet in reais', async () => {
    const workspace = seedMockFamily()
    const { result } = renderHookWithProviders(() => useAccounts(workspace.id))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toHaveLength(1)
    expect(result.current.data?.[0]).toMatchObject({
      name: 'Carteira',
      balance: { currency: 'BRL' },
    })
  })

  it('lists the month and sums it on the dashboard', async () => {
    const workspace = seedMockFamily()
    await postTransaction(workspace.id, {
      id: '0192f3a4-5b6c-7d8e-9f01-23456789abcd',
      kind: 'EXPENSE',
      amount: { amount: '12.50', currency: 'BRL' },
      occurredOn: '2026-11-03',
      accountId: mockState.accounts[0].id,
      categoryId: mockState.categories[0].id,
    })
    const { result } = renderHookWithProviders(() => ({
      transactions: useTransactions(workspace.id, '2026-11'),
      dashboard: useDashboard(workspace.id, '2026-11'),
    }))

    await waitFor(() => expect(result.current.transactions.isSuccess).toBe(true))
    await waitFor(() => expect(result.current.dashboard.isSuccess).toBe(true))
    expect(result.current.transactions.data).toMatchObject([
      { id: '0192f3a4-5b6c-7d8e-9f01-23456789abcd', description: null },
    ])
    expect(result.current.dashboard.data).toMatchObject({
      month: '2026-11',
      income: { amount: '0.00', currency: 'BRL' },
      expenses: { amount: '12.50', currency: 'BRL' },
      result: { amount: '-12.50', currency: 'BRL' },
      balance: { amount: '-12.50', currency: 'BRL' },
      byCategory: [
        {
          categoryId: mockState.categories[0].id,
          current: { amount: '12.50', currency: 'BRL' },
          previous: { amount: '0.00', currency: 'BRL' },
        },
      ],
    })
    const daily = result.current.dashboard.data?.dailyBalance ?? []
    expect(daily).toHaveLength(30)
    expect(daily[1].balance.amount).toBe('0.00')
    expect(daily[2].balance.amount).toBe('-12.50')
  })
})

describe('prefetchWorkspace', () => {
  it('fills the cache the screens read: accounts, both category lists, the dashboard and the month', async () => {
    const workspace = seedMockFamily()
    const queryClient = createTestQueryClient()

    await prefetchWorkspace(queryClient, workspace.id, '2026-11')

    expect(queryClient.getQueryData(queryKeys.accounts(workspace.id))).toHaveLength(1)
    expect(queryClient.getQueryData(queryKeys.categories(workspace.id, 'EXPENSE'))).toHaveLength(13)
    expect(queryClient.getQueryData(queryKeys.categories(workspace.id, 'INCOME'))).toHaveLength(6)
    expect(queryClient.getQueryData(queryKeys.dashboard(workspace.id, '2026-11'))).toMatchObject({
      month: '2026-11',
    })
    expect(queryClient.getQueryData(queryKeys.transactions(workspace.id, '2026-11'))).toEqual([])
  })
})
