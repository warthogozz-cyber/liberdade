import { useIntl } from 'react-intl'
import type { FounderTheme } from '@/shared/api/billing'
import { founderThemes, THEME_ORDER } from './founderThemes'
import './register'

interface ThemePickerProps {
  value: FounderTheme
  disabled: boolean
  onPick: (theme: FounderTheme) => void
}

/** The four Founder themes, one at a time. Native radios: one tab stop, arrow keys, focus kept. */
export function ThemePicker({ value, disabled, onPick }: ThemePickerProps) {
  const intl = useIntl()
  return (
    <div
      role="radiogroup"
      aria-label={intl.formatMessage({ id: 'billing.theme' })}
      className="flex flex-wrap gap-3"
    >
      {THEME_ORDER.map((theme) => {
        const { accent, label } = founderThemes[theme]
        const selected = theme === value
        return (
          <label
            key={theme}
            className={`flex min-h-12 items-center gap-2 rounded-field border px-3 has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary ${selected ? 'border-2' : 'border-border'} ${disabled ? 'opacity-50' : ''}`}
            style={selected ? { borderColor: accent } : undefined}
          >
            <input
              type="radio"
              name="founder-theme"
              className="sr-only"
              checked={selected}
              disabled={disabled}
              onChange={() => onPick(theme)}
            />
            <span
              aria-hidden="true"
              className="size-5 rounded-full"
              style={{ backgroundColor: accent }}
            />
            {label}
            {/* The selection is not shown by colour alone. */}
            {selected && <span aria-hidden="true">✓</span>}
          </label>
        )
      })}
    </div>
  )
}
