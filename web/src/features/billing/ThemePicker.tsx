import { useIntl } from 'react-intl'
import type { FounderTheme } from '@/shared/api/billing'
import { founderThemes, THEME_ORDER } from './founderThemes'
import './register'

interface ThemePickerProps {
  value: FounderTheme
  disabled: boolean
  onPick: (theme: FounderTheme) => void
}

/** The four Founder themes, one at a time. */
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
          <button
            key={theme}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={disabled}
            onClick={() => onPick(theme)}
            className={`flex min-h-12 items-center gap-2 rounded-field border px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50 ${selected ? 'border-2' : 'border-border'}`}
            style={selected ? { borderColor: accent } : undefined}
          >
            <span
              aria-hidden="true"
              className="size-5 rounded-full"
              style={{ backgroundColor: accent }}
            />
            {label}
          </button>
        )
      })}
    </div>
  )
}
