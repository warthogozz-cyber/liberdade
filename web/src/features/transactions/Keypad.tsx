import { useEffect, useEffectEvent } from 'react'
import { useIntl } from 'react-intl'
import { appendDigit, appendDoubleZero, removeLastDigit } from '@/shared/money'

interface KeypadProps {
  /** Receives how to change the amount, in cents. */
  onChange: (update: (cents: bigint) => bigint) => void
  /** Whether the physical keyboard types too; off while a sheet is open. */
  keyboard: boolean
}

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9'] as const

/** Whether typing in `target` belongs to that field, not to the amount. */
function isTextEntry(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return true
  return target instanceof HTMLInputElement && target.type !== 'radio' && target.type !== 'checkbox'
}

const key =
  'min-h-14 rounded-field bg-surface text-2xl font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary active:bg-bg'

/** The amount's keypad: digits, 00 and erase, plus the physical keyboard's digits and Backspace. */
export function Keypad({ onChange, keyboard }: KeypadProps) {
  const intl = useIntl()

  const onKeyDown = useEffectEvent((event: KeyboardEvent) => {
    if (!keyboard || event.ctrlKey || event.altKey || event.metaKey) return
    if (isTextEntry(event.target)) return
    if (/^[0-9]$/.test(event.key)) {
      event.preventDefault()
      onChange((cents) => appendDigit(cents, Number(event.key)))
    } else if (event.key === 'Backspace') {
      event.preventDefault()
      onChange(removeLastDigit)
    }
  })

  useEffect(() => {
    const listener = (event: KeyboardEvent) => onKeyDown(event)
    document.addEventListener('keydown', listener)
    return () => document.removeEventListener('keydown', listener)
  }, [])

  return (
    <div className="grid grid-cols-3 gap-2">
      {KEYS.map((digit) => (
        <button
          key={digit}
          type="button"
          className={key}
          onClick={() => onChange((cents) => appendDigit(cents, Number(digit)))}
        >
          {digit}
        </button>
      ))}
      <button
        type="button"
        className={key}
        aria-label={intl.formatMessage({ id: 'expense.delete' })}
        onClick={() => onChange(removeLastDigit)}
      >
        <span aria-hidden="true">⌫</span>
      </button>
      <button
        type="button"
        className={key}
        onClick={() => onChange((cents) => appendDigit(cents, 0))}
      >
        0
      </button>
      <button type="button" className={key} onClick={() => onChange(appendDoubleZero)}>
        00
      </button>
    </div>
  )
}
