import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { FormattedMessage } from 'react-intl'
import { Navigate, Outlet } from 'react-router'
import { ApiError, postTransaction, queryKeys, useMe } from '@/shared/api'
import { applySentTransaction, startOutboxSync } from '@/shared/offline'

/**
 * Every screen of a signed-in person sits under this layout. It waits for `/v1/me`, sends a 401 to
 * Entrar, and sends the person's queue while it is open.
 */
export function SessionLayout() {
  const queryClient = useQueryClient()
  const me = useMe()
  const userId = me.data?.user.id

  // The cleanup stops the sync and aborts a run in flight; a new user starts their own (RF2).
  useEffect(() => {
    if (!userId) return
    return startOutboxSync({
      userId,
      send: (item) => postTransaction(item.workspaceId, item.payload),
      onSent: (item, view) => applySentTransaction(queryClient, item.workspaceId, view),
      // The 401 of `/v1/me` then reaches the app's global handler.
      onUnauthorized: () => void queryClient.invalidateQueries({ queryKey: queryKeys.me }),
    })
  }, [userId, queryClient])

  if (me.error instanceof ApiError && me.error.status === 401) {
    return <Navigate to="/entrar" replace />
  }
  // Offline with a cached `me`, the screens open with the cache.
  if (me.data) return <Outlet />
  if (me.isError) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
        <p role="alert">
          <FormattedMessage id="common.error" />
        </p>
        <button
          type="button"
          className="min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          onClick={() => void me.refetch()}
        >
          <FormattedMessage id="common.retry" />
        </button>
      </main>
    )
  }
  // Also while the saved cache is being restored: nothing redirects until `me` is known.
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <p role="status">
        <FormattedMessage id="common.loading" />
      </p>
    </main>
  )
}
