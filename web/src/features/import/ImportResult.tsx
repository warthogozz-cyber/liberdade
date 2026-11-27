import { FormattedMessage } from 'react-intl'
import { Link } from 'react-router'
import type { ImportTotals } from './useImport'
import './register'

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

interface ImportResultProps {
  totals: ImportTotals
  /** How many checked lines there were, to say how many of them went when a batch failed. */
  total: number
  online: boolean
  retrying: boolean
  onRetry: () => void
  onAnother: () => void
}

/** What the import did. It only informs: everything already happened, and a retry repeats nothing. */
export function ImportResult({
  totals,
  total,
  online,
  retrying,
  onRetry,
  onAnother,
}: ImportResultProps) {
  const partial = totals.failedAfter !== null
  const retryable = totals.failure?.retryable ?? true
  const detail = totals.failure?.detail ?? null
  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold">
        <FormattedMessage id={partial ? 'import.result.partialTitle' : 'import.result.title'} />
      </h2>
      <div role="status" className="flex flex-col gap-1">
        {totals.created > 0 && (
          <p>
            <FormattedMessage id="import.result.created" values={{ n: totals.created }} />
          </p>
        )}
        {totals.confirmed > 0 && (
          <p>
            <FormattedMessage id="import.result.confirmed" values={{ n: totals.confirmed }} />
          </p>
        )}
        {totals.skipped > 0 && (
          <p>
            <FormattedMessage id="import.result.skipped" values={{ n: totals.skipped }} />
          </p>
        )}
      </div>
      {partial && (
        <>
          <p>
            <FormattedMessage
              id="import.result.partial"
              values={{ sent: totals.failedAfter, total }}
            />
          </p>
          {retryable ? (
            <>
              {!online && (
                <p>
                  <FormattedMessage id="import.offline" />
                </p>
              )}
              <button
                type="button"
                disabled={!online || retrying}
                onClick={onRetry}
                className={`${button} bg-primary text-on-primary`}
              >
                <FormattedMessage id="import.result.retry" />
              </button>
            </>
          ) : (
            <p role="alert">
              {detail ? (
                <FormattedMessage id="import.result.rejected" values={{ detail }} />
              ) : (
                <FormattedMessage id="import.result.rejectedGeneric" />
              )}
            </p>
          )}
        </>
      )}
      <Link
        to="/lancamentos"
        className={`${button} ${partial ? 'border border-primary text-primary' : 'bg-primary text-on-primary'}`}
      >
        <FormattedMessage id="import.result.list" />
      </Link>
      <button
        type="button"
        onClick={onAnother}
        className={`${button} border border-primary text-primary`}
      >
        <FormattedMessage id="import.result.another" />
      </button>
    </section>
  )
}
