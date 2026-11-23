import { useQuery } from '@tanstack/react-query'
import { useCallback } from 'react'
import type { MeView, MeWorkspace } from './types'
import { DEFAULT_CURRENCY, DEFAULT_LOCALE, formatMoney } from '@/shared/money'
import { useChosenWorkspace, useCurrentWorkspace } from './hooks'
import { queryKeys } from './queryKeys'

const NO_WORKSPACES: MeWorkspace[] = []

/** The current family's locale; the default one while there is no family. */
export function useLocale(): string {
  return useCurrentWorkspace()?.locale ?? DEFAULT_LOCALE
}

/** Writes cents in the current family's currency and locale. Stable until either changes. */
export function useFormatMoney(): (cents: bigint) => string {
  const workspace = useCurrentWorkspace()
  const currency = workspace?.currency ?? DEFAULT_CURRENCY
  const locale = workspace?.locale ?? DEFAULT_LOCALE
  return useCallback((cents: bigint) => formatMoney(cents, currency, locale), [currency, locale])
}

/**
 * The family's locale from what is already cached (restored from the device, or fetched by the
 * session), without ever requesting `/v1/me`: the root provider uses it, and a signed-out visitor on a
 * public route must not fire a request whose 401 would send them to sign-in.
 */
export function useCachedLocale(): string {
  const me = useQuery<MeView>({ queryKey: queryKeys.me, enabled: false })
  const workspace = useChosenWorkspace(me.data?.workspaces ?? NO_WORKSPACES)
  return workspace?.locale ?? DEFAULT_LOCALE
}
