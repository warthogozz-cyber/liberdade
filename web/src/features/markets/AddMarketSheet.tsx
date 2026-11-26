import { FormattedMessage, useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import type { MarketCard } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { Sheet } from '@/shared/ui/Sheet'
import { MarketDetailsForm } from './MarketDetailsForm'
import { useAddMarket } from './useMarketWrites'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

interface AddMarketSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  workspaceId: string
  geocodingEnabled: boolean
  onCreated: (card: MarketCard) => void
}

/** The OpenStreetMap page that explains how to put a missing place on the map. */
export const OSM_FIX_THE_MAP_URL = 'https://www.openstreetmap.org/fixthemap'

/** "Adicionar mercado": a name, a kind and, if the person has it, the position. */
export function AddMarketSheet({
  open,
  onOpenChange,
  workspaceId,
  geocodingEnabled,
  onCreated,
}: AddMarketSheetProps) {
  const intl = useIntl()
  const add = useAddMarket(workspaceId)
  const error = !add.isError
    ? null
    : add.error instanceof ApiError && add.error.detail
      ? add.error.detail
      : intl.formatMessage({ id: 'markets.form.saveFailed' })
  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={<FormattedMessage id="markets.add.title" />}
    >
      <div className="flex flex-col gap-4">
        <MarketDetailsForm
          workspaceId={workspaceId}
          geocodingEnabled={geocodingEnabled}
          requirePosition={false}
          pending={add.isPending}
          error={error}
          onSubmit={(body) =>
            add.mutate(body, {
              onSuccess: (card) => {
                onOpenChange(false)
                onCreated(card)
              },
            })
          }
        />
        <p className="text-sm text-text-muted">
          <FormattedMessage id="markets.add.osmHelp" />{' '}
          <a
            href={OSM_FIX_THE_MAP_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="font-medium text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <FormattedMessage id="markets.add.osmLink" />
          </a>
        </p>
      </div>
    </Sheet>
  )
}
