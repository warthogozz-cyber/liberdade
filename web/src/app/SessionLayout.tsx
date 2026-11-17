import { useQueryClient } from '@tanstack/react-query'
import { lazy, Suspense, useEffect } from 'react'
import { FormattedMessage } from 'react-intl'
import { Navigate, Outlet, useNavigate } from 'react-router'
import { PendingGate } from '@/app/PendingGate'
import { useDocuments } from '@/features/legal/useDocuments'
import { ApiError, postTransaction, queryKeys, useMe, type MeView } from '@/shared/api'
import {
  applySentTransaction,
  clearSession,
  onSessionMessage,
  startOutboxSync,
} from '@/shared/offline'

// The acceptance screen is rare (a new person, or a new version): it loads only when needed.
const DocumentsGate = lazy(() =>
  import('@/features/legal/DocumentsGate').then((m) => ({ default: m.DocumentsGate })),
)

function logCleanupFailure(error: unknown): void {
  console.error('Could not clear the session after another tab changed it', error)
}

function Loading() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <p role="status">
        <FormattedMessage id="common.loading" />
      </p>
    </main>
  )
}

/**
 * The family's screens, unless a document waits for the person's accept. Only an answer that says
 * so puts the gate up: while the first try is out, a moment of loading; once it has failed, or
 * without a connection, the screens open (D13).
 */
function DocumentsGuard() {
  const { pending, loading } = useDocuments()
  if (pending && pending.length > 0) {
    return (
      <Suspense fallback={<Loading />}>
        <DocumentsGate />
      </Suspense>
    )
  }
  return loading ? <Loading /> : <Outlet />
}

/**
 * Every screen of a signed-in person sits under this layout. It waits for `/v1/me`, sends a 401 to
 * Entrar, and sends the person's queue while it is open.
 */
export function SessionLayout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const me = useMe()
  const userId = me.data?.user.id

  // The cleanup stops the sync and aborts a run in flight; a new user starts their own (RF2).
  useEffect(() => {
    if (!userId) return
    return startOutboxSync({
      userId,
      send: (item, signal) => postTransaction(item.workspaceId, item.payload, signal),
      onSent: (item, view) => applySentTransaction(queryClient, item.workspaceId, view),
      // The 401 of `/v1/me` then reaches the app's global handler.
      onUnauthorized: () => void queryClient.invalidateQueries({ queryKey: queryKeys.me }),
    })
  }, [userId, queryClient])

  // Another tab signed out, or signed in as somebody else (D26).
  useEffect(
    () =>
      onSessionMessage((message) => {
        if (message.type === 'signed-out') {
          void Promise.resolve(navigate('/entrar'))
            .then(() => clearSession(queryClient))
            .catch(logCleanupFailure)
          return
        }
        const cached = queryClient.getQueryData<MeView>(queryKeys.me)?.user.id
        if (cached !== undefined && cached !== message.userId) {
          void clearSession(queryClient)
            .catch(logCleanupFailure)
            .then(() => window.location.assign('/inicio'))
        }
      }),
    [navigate, queryClient],
  )

  if (me.error instanceof ApiError && me.error.status === 401) {
    return <Navigate to="/entrar" replace />
  }
  // Offline with a cached `me`, the screens open with the cache.
  if (me.data) return me.data.user.deletionScheduledFor ? <PendingGate /> : <DocumentsGuard />
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
  return <Loading />
}
