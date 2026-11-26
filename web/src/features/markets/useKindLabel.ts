import { useIntl } from 'react-intl'
import type { MarketKind } from '@/shared/api/markets'

/** The kind's name as the list shows it ("Supermercado"). */
export function useKindLabel(): (kind: MarketKind | null) => string {
  const intl = useIntl()
  return (kind) => intl.formatMessage({ id: `markets.kind.${kind ?? 'OTHER'}` })
}
