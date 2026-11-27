import { useId, useState, type KeyboardEvent } from 'react'
import { useIntl } from 'react-intl'
import { useCurrentWorkspace, useTags } from '@/shared/api'

interface TagInputProps {
  value: string[]
  onChange: (names: string[]) => void
}

const MAX_TAGS = 5
const MAX_LENGTH = 30
const SUGGESTIONS = 5

const sameName = (a: string, b: string) => a.toLowerCase() === b.toLowerCase()

/**
 * The names of a transaction's tags: typed (Enter or a comma adds) or picked from the family's tags.
 * It mirrors the API's rules, which stay the judge: names trimmed, at most 30 characters, none twice
 * ignoring case, at most five.
 */
export function TagInput({ value, onChange }: TagInputProps) {
  const intl = useIntl()
  const inputId = useId()
  const listId = useId()
  const helpId = useId()
  const tags = useTags(useCurrentWorkspace()?.id ?? '')
  const [text, setText] = useState('')
  const [active, setActive] = useState(-1)
  const [tooLong, setTooLong] = useState(false)
  const [refused, setRefused] = useState(false)

  const typed = text.trim().toLowerCase()
  const suggestions =
    typed === ''
      ? []
      : (tags.data ?? [])
          .filter(
            ({ name }) =>
              name.toLowerCase().startsWith(typed) && !value.some((each) => sameName(each, name)),
          )
          .slice(0, SUGGESTIONS)
  const full = value.length >= MAX_TAGS

  /** The names after adding one, or undefined when it is refused (and says why). */
  function added(names: string[], raw: string): string[] | undefined {
    const name = raw.trim()
    if (name === '' || names.some((each) => sameName(each, name))) return names
    if ([...name].length > MAX_LENGTH) {
      setTooLong(true)
      return undefined
    }
    if (names.length >= MAX_TAGS) {
      setRefused(true)
      return undefined
    }
    // The family's own spelling wins over what was typed.
    const known = tags.data?.find((tag) => sameName(tag.name, name))
    return [...names, known?.name ?? name]
  }

  function change(next: string) {
    setTooLong(false)
    setRefused(false)
    setActive(-1)
    if (!next.includes(',')) {
      setText(next)
      return
    }
    // A comma closes every name before it; what follows the last one stays in the field.
    const parts = next.split(',')
    const rest = parts.pop() ?? ''
    let names = value
    for (const part of parts) names = added(names, part) ?? names
    if (names !== value) onChange(names)
    setText(rest)
  }

  function take(raw: string) {
    const names = added(value, raw)
    if (!names) return
    if (names !== value) onChange(names)
    setText('')
    setActive(-1)
  }

  function keyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' && suggestions.length > 0) {
      event.preventDefault()
      setActive((index) => (index + 1) % suggestions.length)
    } else if (event.key === 'ArrowUp' && suggestions.length > 0) {
      event.preventDefault()
      setActive((index) => (index <= 0 ? suggestions.length - 1 : index - 1))
    } else if (event.key === 'Enter') {
      // Enter adds a tag here; it never submits or closes anything around the field.
      event.preventDefault()
      take(active >= 0 ? (suggestions[active]?.name ?? text) : text)
    }
  }

  const message = tooLong
    ? intl.formatMessage({ id: 'tags.tooLong' })
    : full || refused
      ? intl.formatMessage({ id: 'tags.max' })
      : ''

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="font-medium">
        {intl.formatMessage({ id: 'tags.label' })}
      </label>
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {value.map((name) => (
            <li
              key={name}
              className="flex items-center gap-1 rounded-field bg-surface pl-3 text-sm"
            >
              {name}
              <button
                type="button"
                aria-label={intl.formatMessage({ id: 'tags.remove' }, { name })}
                onClick={() => onChange(value.filter((each) => each !== name))}
                className="flex min-h-12 min-w-12 items-center justify-center rounded-field focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <span aria-hidden="true">×</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        id={inputId}
        type="text"
        role="combobox"
        aria-expanded={suggestions.length > 0}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-describedby={helpId}
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
        autoComplete="off"
        placeholder={intl.formatMessage({ id: 'tags.placeholder' })}
        value={text}
        onChange={(event) => change(event.target.value)}
        onKeyDown={keyDown}
        // Leaving the field (a tap on Salvar does) confirms what was typed, so it is never lost.
        onBlur={() => {
          if (text.trim() !== '') take(text)
        }}
        className="min-h-12 rounded-field border border-text-muted/30 bg-surface px-3"
      />
      <p id={helpId} className="text-sm text-text-muted">
        {intl.formatMessage({ id: 'transactions.tags.help' })}
      </p>
      <ul
        id={listId}
        role="listbox"
        aria-label={intl.formatMessage({ id: 'tags.suggestions' })}
        hidden={suggestions.length === 0}
        className="flex flex-col rounded-field border border-text-muted/30 bg-surface"
      >
        {suggestions.map(({ id, name }, index) => (
          <li
            key={id}
            id={`${listId}-${index}`}
            role="option"
            aria-selected={index === active}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => take(name)}
            className={`flex min-h-12 cursor-pointer items-center px-3 ${index === active ? 'bg-primary/10 font-semibold' : ''}`}
          >
            {name}
          </li>
        ))}
      </ul>
      <p role="status" className="min-h-5 text-sm text-text-muted">
        {message}
      </p>
    </div>
  )
}
