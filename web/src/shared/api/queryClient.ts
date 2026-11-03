import {
  MutationCache,
  QueryCache,
  QueryClient,
  timeoutManager,
  type TimeoutProvider,
} from '@tanstack/react-query'
import { ApiError } from './errors'

/** How long cached data lives, in memory and on the device: 30 days. */
export const CACHE_MAX_AGE = 30 * 24 * 60 * 60 * 1000

/** The longest delay setTimeout honours (about 24.8 days); a longer one overflows and fires at once. */
const MAX_TIMER_DELAY = 2 ** 31 - 1

/**
 * TanStack Query hands `gcTime` straight to setTimeout, and 30 days overflows it: every unused query
 * would be dropped at once. Clamping keeps an unused query for about 24.8 days in a page left open;
 * the copy on the device still lasts the full `maxAge`.
 */
const clampedTimers: TimeoutProvider<number> = {
  setTimeout: (callback, delay) => setTimeout(callback, Math.min(delay, MAX_TIMER_DELAY)),
  clearTimeout: (id) => clearTimeout(id),
  setInterval: (callback, delay) => setInterval(callback, Math.min(delay, MAX_TIMER_DELAY)),
  clearInterval: (id) => clearInterval(id),
}

/** Retries a failed query up to twice, but never a 4xx: asking again gets the same answer. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 2
}

/** The app's QueryClient: offline first, a 30-day cache, and every 401 sent to `onUnauthorized`. */
export function createQueryClient(onUnauthorized: () => void): QueryClient {
  timeoutManager.setTimeoutProvider(clampedTimers)
  const onError = (error: unknown) => {
    if (error instanceof ApiError && error.status === 401) onUnauthorized()
  }
  return new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: {
        networkMode: 'offlineFirst',
        gcTime: CACHE_MAX_AGE,
        staleTime: 30_000,
        retry: shouldRetry,
      },
      mutations: { retry: false },
    },
  })
}
