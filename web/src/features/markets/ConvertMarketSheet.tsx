import { FormattedMessage } from 'react-intl'
import type { MarketCard } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { Sheet } from '@/shared/ui/Sheet'
import { MarketDetailsForm } from './MarketDetailsForm'
import { useConvertMarket, useWriteError } from './useMarketWrites'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

interface ConvertMarketSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  workspaceId: string
  /** The market's ref (an OSM market that has a row of the family). */
  marketRef: string
  geocodingEnabled: boolean
  onConverted: (card: MarketCard) => void
}

/**
 * Turns a market that left OpenStreetMap into one of the family's: only what the person types is
 * sent, so nothing from the map is copied into the family's data.
 */
export function ConvertMarketSheet({
  open,
  onOpenChange,
  workspaceId,
  marketRef,
  geocodingEnabled,
  onConverted,
}: ConvertMarketSheetProps) {
  const convert = useConvertMarket(workspaceId, marketRef)
  const error = useWriteError(convert)
  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={<FormattedMessage id="markets.convert.title" />}
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.convert.help" />
        </p>
        <MarketDetailsForm
          workspaceId={workspaceId}
          geocodingEnabled={geocodingEnabled}
          requirePosition
          pending={convert.isPending}
          error={error}
          onSubmit={(body) =>
            convert.mutate(body, {
              onSuccess: (card) => {
                onOpenChange(false)
                onConverted(card)
              },
            })
          }
        />
      </div>
    </Sheet>
  )
}
