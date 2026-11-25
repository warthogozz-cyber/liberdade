# Banco offline sob demanda: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan
> task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take Dexie (26,4 KB gzip) out of the initial load, so the 200 KB budget has room again
before Plan 9.2, without changing what the person sees or how the offline queue behaves.

**Architecture:** `web/src/shared/offline/db.ts` becomes the only module that imports `dexie` at
runtime, and nothing in the initial load imports it statically. A small loader,
`openOfflineDb()`, imports it with `import()` once and memoizes the promise. The outbox, the sync,
the query-cache persister and `useOutbox` reach the database through that loader. `useOutbox`
stops using `dexie-react-hooks` and subscribes to Dexie's `liveQuery`, which the lazy module
exposes. A build guard keeps `db.ts` and the `dexie` package out of the initial load.

**Tech Stack:** React 19, Dexie 4.4.7, TanStack Query persist client, Vite 8 with
vite-plugin-pwa (precaches every `**/*.js`), Vitest + fake-indexeddb.

**Spec:** none. The maintainer's decisions on 2026-11-25 are the spec: keep the 200 KB initial-load
budget; cut weight before 9.2; scope is **only** Dexie on demand (no message precompilation, no
lazy toasts); a short plan executed in this session with one final whole-branch review on the most
capable model.

## Global Constraints

- The initial-load budget stays `initialKb: 200` in `web/scripts/chunk-budget.mjs` (do not lower or
  raise it).
- No visible change: no new texts, screens or states. `useOutbox` keeps returning `undefined` while
  loading and `[]` without a user.
- The offline queue keeps working in airplane mode once the app is installed (US-03, S1): the lazy
  chunk is precached by the service worker, like every other JS file.
- The database name, version and stores do not change: `new Dexie('liberdade')`, `version(1)`,
  `outbox: 'id, [userId+createdAt], workspaceId'`, `kv: 'key'`. Existing devices keep their data.
- Someone else's queue never shows (RF2): the per-user guard in `useOutbox` stays.
- English for code and commits, PT-BR for docs. No personal data in files.

## Review Focus

1. **Two first callers at once** (the persister restoring the cache and the sync starting at boot):
   one Dexie instance only. `openOfflineDb()` returns the same promise to both.
2. **The lazy chunk fails to load** (a tab open across a deploy, a flaky network on a cold visit):
   the rejected promise is not memoized, so the next call tries again; callers keep their current
   error handling (`saveQueryCacheNow` never rejects; the sync's failed run retries).
3. **User or family switch while the subscription is open**: `useOutbox` never returns another
   user's or another family's items, and unsubscribes on unmount and on change.
4. **Cross-tab updates**: an item queued in another tab still shows in this tab's list, as with
   `useLiveQuery` today (Dexie 4's `liveQuery` broadcasts between tabs).
5. **Reload with a saved cache**: the persister still restores the query cache after a reload,
   now through the loader.

---

### Task 1: The database loads on demand

**Files:**
- Create: `web/src/shared/offline/types.ts` (move `OutboxItem` and `KvRow` here, unchanged)
- Create: `web/src/shared/offline/openOfflineDb.ts`
- Modify: `web/src/shared/offline/db.ts`, `outbox.ts`, `sync.ts`, `persister.ts`, `useOutbox.ts`,
  `index.ts`
- Modify: `web/src/features/privacy/PendingActions.tsx` (the `userItems(...).modify` call)
- Modify: `web/src/test-setup.ts` (import `db` from `@/shared/offline/db` directly; tests are not
  bundled)
- Modify: `web/package.json`, `pnpm-lock.yaml` (remove `dexie-react-hooks`)
- Test: `web/src/shared/offline/openOfflineDb.test.ts` (new), existing `outbox.test.ts`,
  `sync.test.ts`, `persister.test.ts`, `useOutbox.test.tsx`, `applySent.test.ts`, `undo.test.ts`

**Interfaces:**
- `db.ts` (the lazy module) exports: `db` (unchanged), `userCollection(userId: string)` (the
  `[userId+createdAt]` range query that `userItems` builds today, with `Dexie.minKey/maxKey`), and
  `watch<T>(query: () => Promise<T>, onValue: (value: T) => void): () => void` (wraps Dexie's
  `liveQuery(query).subscribe`, returns the unsubscribe; an error in the query is ignored, keeping
  the last value, as `useLiveQuery` did).
- `openOfflineDb.ts` exports `type OfflineDb = typeof import('./db')` and
  `openOfflineDb(): Promise<OfflineDb>`: memoizes the `import('./db')` promise; on rejection it
  clears the memo before rethrowing.
- `outbox.ts`: `userItems(userId)` becomes `async` and returns the same Dexie `Collection`
  (`(await openOfflineDb()).userCollection(userId)`); every `db.` use becomes
  `const { db } = await openOfflineDb()`. Only `import type` from `dexie` is allowed outside `db.ts`.
- `index.ts` no longer exports `db`; it keeps every other export. `OutboxItem` comes from
  `types.ts`.

- [ ] **Step 1: Write the failing loader test** `openOfflineDb.test.ts`:
  `returnsOneInstanceToConcurrentCallers` (two calls without awaiting resolve to the same `db`
  object) and `retriesAfterAFailedImport` (with the import mocked to reject once, the first call
  rejects and the second resolves). Run `corepack pnpm --dir web test openOfflineDb`; expect FAIL
  (module missing).
- [ ] **Step 2: Implement** `types.ts`, `openOfflineDb.ts`, the `db.ts` exports, and switch
  `outbox.ts`, `sync.ts`, `persister.ts`, `PendingActions.tsx` and `test-setup.ts` to the loader.
- [ ] **Step 3: Rewrite `useOutbox`** on `watch` from the loader, inside a `useEffect` keyed on
  `userId` and `workspaceId`: the snapshot carries `userId` and `workspaceId`, a stale subscription
  is cancelled before it starts if the effect already cleaned up, and the existing guard returns
  `undefined` for a snapshot of another user or family. Remove `dexie-react-hooks`
  (`corepack pnpm --dir web remove dexie-react-hooks`).
- [ ] **Step 4: Add the switch test** to `useOutbox.test.tsx`: `neverShowsTheLastUsersItemsAfterASwitch`
  (render with user A with one queued item, rerender with user B: the hook returns `undefined` or
  B's items, never A's item). Keep the existing cross-tab/live update tests passing.
- [ ] **Step 5: Verify:** `corepack pnpm --dir web test` (all pass), `typecheck`, `lint`,
  `format:check`.
- [ ] **Step 6: Commit** `perf(web): load the offline database on demand`.

### Task 2: A guard keeps it out of the first load

**Files:**
- Modify: `web/scripts/check-chunks.mjs` (the `lazyOnly` sources)
- Modify: `web/scripts/chunk-budget.mjs` (the header comment: the new measured initial load)
- Test: `web/scripts/chunk-budget.test.mjs` only if `lazyOnly` needs a change (it should not)

- [ ] **Step 1:** Add `'src/shared/offline/db.ts'` and the `dexie` package's entry module, as its
  path ends in `chunk-modules.json` (e.g. `node_modules/dexie/dist/dexie.mjs`; read the real id from
  the build), to the sources passed to `lazyOnly`, with a comment naming this plan's decision.
- [ ] **Step 2:** `corepack pnpm --dir web build` then `check:chunks`: exit 0, `dexie` no longer in
  "Initial load by node_modules package", the initial load near 174 KB. Write the measured number in
  the `chunk-budget.mjs` header comment (the limit stays 200).
- [ ] **Step 3: Prove the guard bites:** temporarily add `import '@/shared/offline/db'` to
  `web/src/main.tsx`, build, and see `check:chunks` exit 1 naming the offender; revert.
- [ ] **Step 4: Commit** `build(web): keep the offline database out of the first load`.

### Finish

- [ ] Both privacy checks exit 1, push, open the PR (PT-BR body, the before/after numbers). The e2e
  suite on CI covers the offline queue, the reload with a saved cache and the pending items.
- [ ] One final whole-branch review on the most capable model; one fix wave if needed.
- [ ] Merge on green; add this plan to the plans README in the docs PR that follows.
