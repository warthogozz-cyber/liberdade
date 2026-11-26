import {
  Component,
  lazy,
  Suspense,
  useEffect,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from '../messages.pt-BR'
import { parseNfceInput, type ParsedNfce } from './nfceQr'
import { pickDetector, type QrWindow } from './scanner/detectQr'

registerMessages(marketsMessages)

// The camera reader and its worker load only after the tap on "Ler o QR".
const QrScanner = lazy(() => import('./scanner/QrScanner'))

const KEY_LENGTH = 44

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = 'min-h-12 rounded-field border border-text-muted bg-background px-3 text-base'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`
const primary = `flex min-h-12 items-center justify-center rounded-field border border-primary bg-primary px-4 font-medium text-on-primary disabled:opacity-60 ${focus}`

/**
 * Keeps a reader that fails to load (a dropped connection, a stale deploy) or breaks while opening
 * from taking the screen down: it says so and hands over to paste and type.
 */
class ReaderBoundary extends Component<
  { onFailed: () => void; children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown) {
    console.error('The QR reader failed', error)
    this.props.onFailed()
  }

  render() {
    return this.state.failed ? (
      <p role="alert" className="text-sm">
        <FormattedMessage id="markets.receipt.scanner.failed" />
      </p>
    ) : (
      this.props.children
    )
  }
}

/** The typed key as the screen shows it: no separators, capitals, groups of 4. */
const groupKey = (text: string) =>
  (
    text
      .replace(/[^0-9A-Za-z]/g, '')
      .toUpperCase()
      .slice(0, KEY_LENGTH)
      .match(/.{1,4}/g) ?? []
  ).join(' ')

interface ReceiptKeyStepProps {
  /** True while the API checks the key. */
  pending: boolean
  /** What the API answered to the last key, in words; the screen's own errors come before it. */
  error: string | null
  onParsed: (parsed: ParsedNfce) => void
}

/** The first step: the QR, a pasted link or the typed key, cleaned on the device before anything is sent. */
export function ReceiptKeyStep({ pending, error, onParsed }: ReceiptKeyStepProps) {
  const intl = useIntl()
  const [scanning, setScanning] = useState(false)
  const [scanRun, setScanRun] = useState(0)
  const [input, setInput] = useState<'paste' | 'type' | null>(null)
  const [text, setText] = useState('')
  // The typed key keeps a numeric keyboard until the person says it has letters (positions 7-18).
  const [letters, setLetters] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  // Unknown until the browser has been asked, so the button never flashes where there is no reader.
  const [canScan, setCanScan] = useState<boolean | null>(null)

  useEffect(() => {
    let current = true
    void pickDetector(window as QrWindow).then((kind) => {
      if (current) setCanScan(kind !== 'none')
    })
    return () => {
      current = false
    }
  }, [])

  const handle = (raw: string) => {
    const parsed = parseNfceInput(raw, new Date())
    if ('error' in parsed) {
      setProblem(intl.formatMessage({ id: `markets.receipt.error.${parsed.error}` }))
      return
    }
    setProblem(null)
    onParsed(parsed)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!pending && text.trim() !== '') handle(text)
  }

  const pick = (mode: 'paste' | 'type') => {
    setScanning(false)
    setInput(mode)
    setText('')
    setLetters(false)
    setProblem(null)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-3">
        {canScan && (
          <button
            type="button"
            onClick={() => {
              setInput(null)
              setProblem(null)
              setScanning(true)
              setScanRun((run) => run + 1)
            }}
            className={outline}
          >
            <FormattedMessage id="markets.receipt.key.scan" />
          </button>
        )}
        <button type="button" onClick={() => pick('paste')} className={outline}>
          <FormattedMessage id="markets.receipt.key.paste" />
        </button>
        <button type="button" onClick={() => pick('type')} className={outline}>
          <FormattedMessage id="markets.receipt.key.type" />
        </button>
      </div>

      {scanning && (
        <ReaderBoundary key={scanRun} onFailed={() => setInput((current) => current ?? 'paste')}>
          <Suspense
            fallback={
              <p role="status">
                <FormattedMessage id="common.loading" />
              </p>
            }
          >
            <QrScanner
              onResult={(raw) => {
                setScanning(false)
                handle(raw)
              }}
              // The scanner keeps its own alert on screen; paste and type are the way on.
              onUnavailable={() => setInput((current) => current ?? 'paste')}
            />
          </Suspense>
        </ReaderBoundary>
      )}

      {input !== null && (
        <form onSubmit={submit} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm font-medium">
            <FormattedMessage
              id={
                input === 'paste'
                  ? 'markets.receipt.key.pasteLabel'
                  : 'markets.receipt.key.typeLabel'
              }
            />
            <input
              type="text"
              value={text}
              autoComplete="off"
              spellCheck={false}
              inputMode={input === 'type' ? (letters ? 'text' : 'numeric') : undefined}
              autoCapitalize={input === 'type' && letters ? 'characters' : 'none'}
              onChange={(event) =>
                setText(input === 'type' ? groupKey(event.target.value) : event.target.value)
              }
              className={`${field} font-normal`}
            />
          </label>
          {input === 'type' && (
            <button
              type="button"
              onClick={() => setLetters((current) => !current)}
              className={`${outline} self-start`}
            >
              <FormattedMessage
                id={letters ? 'markets.receipt.key.numbersOnly' : 'markets.receipt.key.letters'}
              />
            </button>
          )}
          <button type="submit" disabled={pending || text.trim() === ''} className={primary}>
            <FormattedMessage
              id={pending ? 'markets.receipt.key.checking' : 'markets.receipt.key.next'}
            />
          </button>
        </form>
      )}

      {(problem ?? error) !== null && <p role="alert">{problem ?? error}</p>}
    </div>
  )
}
