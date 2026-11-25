import { useId } from 'react'
import { useIntl } from 'react-intl'
import { useVentureChoices } from './useVentureChoices'

interface VenturePickerProps {
  value: string | null
  onChange: (id: string | null) => void
  /** The project the transaction already has; it stays on offer even if it is closed. */
  keep: string | null
}

/** "Projeto (opcional)": a select with Nenhum and the projects that are open. */
export function VenturePicker({ value, onChange, keep }: VenturePickerProps) {
  const intl = useIntl()
  const id = useId()
  const choices = useVentureChoices(keep)
  if (!choices) return null
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-medium">
        {intl.formatMessage({ id: 'transaction.venture.label' })}
      </label>
      <select
        id={id}
        value={choices.some((each) => each.id === value) ? (value ?? '') : ''}
        onChange={(event) => onChange(event.target.value || null)}
        className="min-h-12 rounded-field border border-text-muted/30 bg-surface px-3"
      >
        <option value="">{intl.formatMessage({ id: 'transaction.venture.none' })}</option>
        {choices.map(({ id: ventureId, name, status }) => (
          <option key={ventureId} value={ventureId}>
            {status === 'CLOSED'
              ? intl.formatMessage({ id: 'transaction.venture.closed' }, { name })
              : name}
          </option>
        ))}
      </select>
    </div>
  )
}
