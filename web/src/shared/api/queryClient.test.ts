import { MutationObserver } from '@tanstack/react-query'
import { http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { mockState } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import {
  API_URL,
  ApiError,
  createQueryClient,
  createWorkspace,
  getMe,
  queryKeys,
} from '@/shared/api'
import { shouldRetry } from './queryClient'

describe('createQueryClient', () => {
  it('calls onUnauthorized when a query gets a 401', async () => {
    mockState.signedIn = false
    const onUnauthorized = vi.fn()
    const queryClient = createQueryClient(onUnauthorized)

    await expect(
      queryClient.fetchQuery({ queryKey: queryKeys.me, queryFn: getMe }),
    ).rejects.toMatchObject({ status: 401, code: 'auth.required' })

    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('calls onUnauthorized when a mutation gets a 401', async () => {
    mockState.signedIn = false
    const onUnauthorized = vi.fn()
    const queryClient = createQueryClient(onUnauthorized)
    const observer = new MutationObserver(queryClient, { mutationFn: createWorkspace })

    await expect(observer.mutate('Família Teste')).rejects.toMatchObject({ status: 401 })

    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('leaves every other failure to the screen that asked', async () => {
    server.use(
      http.get(`${API_URL}/v1/me`, () =>
        problem(404, 'not-found', 'Não encontramos o que você procurou.'),
      ),
    )
    const onUnauthorized = vi.fn()
    const queryClient = createQueryClient(onUnauthorized)
    const observer = new MutationObserver(queryClient, {
      mutationFn: () => Promise.reject(new Error('boom')),
    })

    await expect(
      queryClient.fetchQuery({ queryKey: queryKeys.me, queryFn: getMe }),
    ).rejects.toMatchObject({ status: 404 })
    await expect(observer.mutate()).rejects.toThrow('boom')

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('keeps queries for 30 days, fresh for 30 s and offline first, and never retries mutations', () => {
    const { queries, mutations } = createQueryClient(() => {}).getDefaultOptions()

    expect(queries).toMatchObject({
      networkMode: 'offlineFirst',
      gcTime: 30 * 24 * 60 * 60 * 1000,
      staleTime: 30_000,
      retry: shouldRetry,
    })
    expect(mutations).toMatchObject({ retry: false })
  })

  it('keeps an unused query past the first timer tick despite the 30-day gcTime', async () => {
    const queryClient = createQueryClient(() => {})
    queryClient.setQueryData(['probe'], 1)

    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(queryClient.getQueryData(['probe'])).toBe(1)
  })
})

describe('shouldRetry', () => {
  it.each([
    [0, 'a 422', new ApiError(422, 'request.invalid'), false],
    [0, 'a 503', new ApiError(503, 'http'), true],
    [1, 'a network failure', new ApiError(0, 'network'), true],
    [2, 'a network failure', new ApiError(0, 'network'), false],
    [0, 'an unknown error', new Error('boom'), true],
  ])('shouldRetry(%i, %s) is %s', (failureCount, _, error, expected) => {
    expect(shouldRetry(failureCount, error)).toBe(expected)
  })
})
