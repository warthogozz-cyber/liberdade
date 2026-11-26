import { useState, type FormEvent } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { NewMarketBody } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { PositionPicker, type PickedPosition } from './PositionPicker'
import { useKindLabel } from './useKindLabel'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

const MAX_NAME = 80
const KINDS: NewMarketBody['kind'][] = ['SUPERMARKET', 'CONVENIENCE', 'GREENGROCER', 'MARKETPLACE']

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'

interface MarketDetailsFormProps {
  workspaceId: string
  geocodingEnabled: boolean
  /** A converted market needs its position; a new one may do without. */
  requirePosition: boolean
  pending: boolean
  /** The text of a failed save, shown above the button. */
  error: string | null
  onSubmit: (body: NewMarketBody) => void
}

/** A typed name, a kind and a position: what a family market is made from, new or converted. */
export function MarketDetailsForm({
  workspaceId,
  geocodingEnabled,
  requirePosition,
  pending,
  error,
  onSubmit,
}: MarketDetailsFormProps) {
  const intl = useIntl()
  const kindLabel = useKindLabel()
  const [name, setName] = useState('')
  const [kind, setKind] = useState<NewMarketBody['kind']>('SUPERMARKET')
  const [position, setPosition] = useState<PickedPosition | null>(null)
  const [problem, setProblem] = useState<'name' | 'position' | null>(null)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const typed = name.trim()
    if (typed === '') return setProblem('name')
    if (requirePosition && position === null) return setProblem('position')
    setProblem(null)
    onSubmit({
      name: typed,
      kind,
      ...(position !== null ? { lat: position.lat, lon: position.lon } : {}),
    })
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1 text-sm">
        <FormattedMessage id="markets.form.name" />
        <input
          type="text"
          value={name}
          maxLength={MAX_NAME}
          onChange={(event) => setName(event.target.value)}
          autoComplete="off"
          className={field}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        <FormattedMessage id="markets.form.kind" />
        <select
          value={kind}
          onChange={(event) => setKind(event.target.value as NewMarketBody['kind'])}
          className={field}
        >
          {KINDS.map((each) => (
            <option key={each} value={each}>
              {kindLabel(each)}
            </option>
          ))}
        </select>
      </label>
      <PositionPicker
        value={position}
        onChange={setPosition}
        workspaceId={workspaceId}
        geocodingEnabled={geocodingEnabled}
      />
      {problem !== null && (
        <p role="alert">
          {intl.formatMessage({
            id: problem === 'name' ? 'markets.form.nameRequired' : 'markets.form.positionRequired',
          })}
        </p>
      )}
      {error !== null && <p role="alert">{error}</p>}
      <button
        type="submit"
        disabled={pending}
        className={`flex min-h-12 items-center justify-center rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-60 ${focus}`}
      >
        <FormattedMessage id={pending ? 'markets.form.saving' : 'markets.form.save'} />
      </button>
    </form>
  )
}
