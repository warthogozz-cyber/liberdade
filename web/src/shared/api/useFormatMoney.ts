import { useCallback } from 'react'
import { DEFAULT_CURRENCY, DEFAULT_LOCALE, formatMoney } from '@/shared/money'
import { useCurrentWorkspace } from './hooks'

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
