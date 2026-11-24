import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { API_URL, queryKeys } from '@/shared/api'
import { invalidatesMe, invalidationPrefixes } from './invalidation'
import { emitLiveChange } from './liveChanges'
import { isLocalChange, markRemoteArrival } from './localChanges'

const DEBOUNCE_MS = 250
const HIDDEN_LIMIT_MS = 5 * 60_000
const RETRY_MS = 60_000
/** `EventSource.CLOSED`, spelled out because the constant is missing where there is no EventSource. */
const CLOSED = 2

type Options = {
  createSource?: (url: string) => EventSource
  now?: () => number
}

function parseChange(data: unknown): { entity: string; id?: string } | null {
  if (typeof data !== 'string') return null
  try {
    const parsed: unknown = JSON.parse(data)
    if (typeof parsed !== 'object' || parsed === null) return null
    const { entity, id } = parsed as { entity?: unknown; id?: unknown }
    if (typeof entity !== 'string') return null
    return typeof id === 'string' ? { entity, id } : { entity }
  } catch {
    return null
  }
}

/**
 * Keeps the family's screens current: opens the family's event stream while the tab is visible and
 * online, and fetches again what a notice says changed. When the stream is not available it falls
 * back to fetching the family again whenever the person comes back to the tab or the network.
 */
export function useLiveSync(workspaceId: string, options?: Options): void {
  const queryClient = useQueryClient()
  const optionsRef = useRef(options)
  useEffect(() => {
    optionsRef.current = options
  })

  useEffect(() => {
    const createSource =
      optionsRef.current?.createSource ??
      (typeof EventSource === 'undefined'
        ? undefined
        : (url: string) => new EventSource(url, { withCredentials: true }))
    if (!createSource) return
    const now = () => (optionsRef.current?.now ?? Date.now)()
    const url = `${API_URL}/v1/workspaces/${workspaceId}/events`

    let source: EventSource | null = null
    let opened = false
    let noStream = false
    let lastAttempt = Number.NEGATIVE_INFINITY
    let hiddenTimer: ReturnType<typeof setTimeout> | undefined
    let debounceTimer: ReturnType<typeof setTimeout> | undefined
    const pending = new Set<string>()

    const invalidate = (prefix: readonly string[]) =>
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, ...prefix] })

    const flush = () => {
      debounceTimer = undefined
      const entities = [...pending]
      pending.clear()
      // The role in `['me']` follows a members change; that key sits outside the family's.
      if (entities.some(invalidatesMe))
        void queryClient.invalidateQueries({ queryKey: queryKeys.me })
      const seen = new Set<string>()
      for (const entity of entities) {
        for (const prefix of invalidationPrefixes(entity)) {
          const key = prefix.join('/')
          if (seen.has(key)) continue
          seen.add(key)
          invalidate(prefix)
        }
      }
    }

    const canOpen = () => document.visibilityState === 'visible' && navigator.onLine

    const close = () => {
      source?.close()
      source = null
    }

    const open = () => {
      if (source || !canOpen()) return
      lastAttempt = now()
      const next = createSource(url)
      source = next
      next.addEventListener('open', () => {
        noStream = false
        // Anything may have changed while the stream was away; the first open has nothing to catch up on.
        if (opened) invalidate([])
        opened = true
      })
      next.addEventListener('changed', (event) => {
        const change = parseChange((event as MessageEvent).data)
        if (!change) return
        emitLiveChange({ workspaceId, ...change })
        if (change.entity === 'transaction' && change.id && !isLocalChange(change.id)) {
          markRemoteArrival(change.id)
        }
        pending.add(change.entity)
        clearTimeout(debounceTimer)
        debounceTimer = setTimeout(flush, DEBOUNCE_MS)
      })
      next.addEventListener('error', () => {
        // Still connecting means the browser is retrying by itself; closed means it gave up (429, 204, 401).
        if (next.readyState !== CLOSED) return
        next.close()
        if (source === next) source = null
        noStream = true
      })
    }

    // Without a stream, coming back is the only moment to fetch again, and retrying is rationed.
    const comeBack = () => {
      if (noStream) {
        invalidate([])
        if (now() - lastAttempt >= RETRY_MS) open()
      } else {
        open()
      }
    }

    const onVisibility = () => {
      clearTimeout(hiddenTimer)
      hiddenTimer = undefined
      if (document.visibilityState === 'hidden') {
        hiddenTimer = setTimeout(close, HIDDEN_LIMIT_MS)
      } else {
        comeBack()
      }
    }
    const onOnline = () => comeBack()
    const onOffline = () => close()

    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    open()

    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
      clearTimeout(hiddenTimer)
      clearTimeout(debounceTimer)
      close()
    }
  }, [queryClient, workspaceId])
}
