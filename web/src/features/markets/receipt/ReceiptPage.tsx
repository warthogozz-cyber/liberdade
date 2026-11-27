import { useEffect, useRef, useState, type ReactNode } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, Navigate } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { ApiError } from '@/shared/api/errors'
import type { ReceiptLookup, SaveReceiptItem } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { marketsMessages } from '../messages.pt-BR'
import { useReceiptLookup, useSaveReceipt } from '../useReceipts'
import { defaultIssuedOn, lookupBody, type ParsedNfce } from './nfceQr'
import { ReceiptItemsStep, type ItemsDraft } from './ReceiptItemsStep'
import { ReceiptKeyStep } from './ReceiptKeyStep'
import { ReceiptMarketStep, type MarketChoice } from './ReceiptMarketStep'
import { PageTip } from '@/shared/help'

registerMessages(marketsMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`
const anchor = `font-medium text-primary underline ${focus}`

function Step({
  id,
  children,
}: {
  id: 'markets.receipt.step.key' | 'markets.receipt.step.market' | 'markets.receipt.step.items'
  children: ReactNode
}) {
  const heading = useRef<HTMLHeadingElement>(null)
  // A later step replaces the one the person just finished: take focus there, so a screen
  // reader announces it and the keyboard does not fall back to the top of the page. The first
  // step opens with the screen and leaves focus where the browser put it.
  useEffect(() => {
    if (id !== 'markets.receipt.step.key') heading.current?.focus()
  }, [id])
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4">
      <h2 ref={heading} id={id} tabIndex={-1} className="text-lg font-medium focus:outline-none">
        <FormattedMessage id={id} />
      </h2>
      {children}
    </section>
  )
}

/**
 * Registers a receipt by hand in three steps (plan 10.2, D17). Everything the person gives lives in
 * this component's memory: leaving the screen drops it, and nothing is written to the device.
 */
export function ReceiptPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'markets.receipt.title' }))
  const workspace = useCurrentWorkspace()
  const ws = workspace?.id ?? ''
  const lookup = useReceiptLookup(ws)
  const save = useSaveReceipt(ws)
  const [today] = useState(() => new Date())
  const [parsed, setParsed] = useState<ParsedNfce | null>(null)
  const [found, setFound] = useState<ReceiptLookup | null>(null)
  const [choice, setChoice] = useState<MarketChoice | null>(null)
  const [draft, setDraft] = useState<ItemsDraft | null>(null)
  const [savedRef, setSavedRef] = useState<string | null>(null)

  if (workspace === undefined) {
    return (
      <main className="mx-auto max-w-lg p-6">
        <p role="status">
          <FormattedMessage id="common.loading" />
        </p>
      </main>
    )
  }
  // Youth and Viewer read prices; registering a receipt is for Owner and Adult.
  if (workspace.role !== 'OWNER' && workspace.role !== 'ADULT') {
    return <Navigate to="/mais/feira" replace />
  }

  const lookupError =
    lookup.error instanceof ApiError && lookup.error.status === 409
      ? intl.formatMessage({ id: 'markets.receipt.error.duplicate' })
      : lookup.error instanceof ApiError && lookup.error.status === 422
        ? intl.formatMessage({ id: 'markets.receipt.error.keyInvalid' })
        : lookup.isError
          ? intl.formatMessage({ id: 'markets.receipt.key.lookupFailed' })
          : null
  // A market the API refuses (another CNPJ, or one already taken) can be swapped without retyping rows.
  const marketRefused =
    save.error instanceof ApiError &&
    (save.error.code === 'receipt.market_cnpj_differs' || save.error.code === 'market.cnpj_taken')
  const saveError =
    save.error instanceof ApiError && save.error.code === 'receipt.duplicate'
      ? intl.formatMessage({ id: 'markets.receipt.error.duplicate' })
      : save.error instanceof ApiError &&
          (save.error.status === 409 || save.error.status === 422) &&
          save.error.detail
        ? save.error.detail
        : save.isError
          ? intl.formatMessage({ id: 'markets.receipt.items.saveFailed' })
          : null

  const changeMarket = () => {
    save.reset()
    setChoice(null)
  }

  const onParsed = (next: ParsedNfce) => {
    setParsed(next)
    lookup.mutate(lookupBody(next), { onSuccess: setFound })
  }

  const onSubmit = (value: { issuedOn: string; items: SaveReceiptItem[] }) => {
    if (parsed === null || choice === null) return
    save.mutate(
      {
        ...lookupBody(parsed),
        issuedOn: value.issuedOn,
        market: choice.market ?? null,
        newMarket: choice.newMarket ?? null,
        items: value.items,
      },
      { onSuccess: (done) => setSavedRef(done.marketRef) },
    )
  }

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-6 p-6">
      <Link to="/mais/feira" className={`${anchor} self-start`}>
        <FormattedMessage id="markets.card.back" />
      </Link>
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="markets.receipt.title" />
      </h1>
      <PageTip id="receipt" />

      {savedRef !== null ? (
        <div className="flex flex-col items-start gap-4">
          <p role="status">
            <FormattedMessage id="markets.receipt.done" />
          </p>
          <Link to={`/mais/feira/mercado/${savedRef}`} className={anchor}>
            <FormattedMessage id="markets.receipt.done.card" />
          </Link>
        </div>
      ) : found === null || parsed === null ? (
        <Step id="markets.receipt.step.key">
          <ReceiptKeyStep pending={lookup.isPending} error={lookupError} onParsed={onParsed} />
        </Step>
      ) : choice === null ? (
        <Step id="markets.receipt.step.market">
          <ReceiptMarketStep ws={ws} known={found.market} onChosen={setChoice} />
        </Step>
      ) : (
        <Step id="markets.receipt.step.items">
          <ReceiptItemsStep
            ws={ws}
            uf={found.uf}
            sefazUrl={parsed.sefazUrl}
            defaultDate={defaultIssuedOn(parsed, today)}
            draft={draft}
            onDraftChange={setDraft}
            pending={save.isPending}
            error={saveError}
            onSubmit={onSubmit}
          />
          {marketRefused && found.market === null && (
            <button type="button" onClick={changeMarket} className={`${outline} self-start`}>
              <FormattedMessage id="markets.receipt.items.changeMarket" />
            </button>
          )}
        </Step>
      )}
    </main>
  )
}
