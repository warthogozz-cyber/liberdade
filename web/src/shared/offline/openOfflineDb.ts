/** The offline database module, loaded on demand so Dexie stays out of the first load. */
export type OfflineDb = typeof import('./db')

/**
 * Wraps `load` so it runs once, sharing its promise between callers. A failed load is forgotten, so
 * the next call tries again (a chunk can fail on a flaky network or across a deploy).
 */
export function loadOnce<T>(load: () => Promise<T>): () => Promise<T> {
  let loading: Promise<T> | undefined
  return () => {
    loading ??= load().catch((error: unknown) => {
      loading = undefined
      throw error
    })
    return loading
  }
}

/** The device's database, loaded once on first use. */
export const openOfflineDb: () => Promise<OfflineDb> = loadOnce(() => import('./db'))
