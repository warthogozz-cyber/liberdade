import { useState } from 'react'
import { FormattedMessage } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { Sheet } from '@/shared/ui/Sheet'
import { PositionPicker, type PickedPosition } from './PositionPicker'
import { useSetPosition, useWriteError } from './useMarketWrites'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

interface GiveLocationSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  workspaceId: string
  marketRef: string
  geocodingEnabled: boolean
}

/** "Dar a localização": the same position picker as a new market, for a family market that has none. */
export function GiveLocationSheet({
  open,
  onOpenChange,
  workspaceId,
  marketRef,
  geocodingEnabled,
}: GiveLocationSheetProps) {
  const save = useSetPosition(workspaceId, marketRef)
  const [position, setPosition] = useState<PickedPosition | null>(null)
  const error = useWriteError(save)
  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={<FormattedMessage id="markets.card.giveLocation" />}
    >
      <div className="flex flex-col gap-4">
        <PositionPicker
          value={position}
          onChange={setPosition}
          workspaceId={workspaceId}
          geocodingEnabled={geocodingEnabled}
        />
        {error !== null && <p role="alert">{error}</p>}
        <button
          type="button"
          disabled={position === null || save.isPending}
          onClick={() => {
            if (position === null) return
            save.mutate(
              { lat: position.lat, lon: position.lon },
              { onSuccess: () => onOpenChange(false) },
            )
          }}
          className="flex min-h-12 items-center justify-center rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-60"
        >
          <FormattedMessage id={save.isPending ? 'markets.form.saving' : 'markets.form.save'} />
        </button>
      </div>
    </Sheet>
  )
}
