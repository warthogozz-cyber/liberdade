import { useEffect, useMemo, useRef, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useSearchParams } from 'react-router'
import { useCategories, useCurrentWorkspace, useDashboard, useMe } from '@/shared/api'
import { usePlan } from '@/shared/api/billing'
import { MIN_MONTH, currentMonth, maxMonth } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import {
  PORTRAIT_HEIGHT,
  PORTRAIT_WIDTH,
  drawPortrait,
  readTokens,
  type PortraitBlock,
} from './drawPortrait'
import { buildPortrait } from './portraitData'
import { sharePortrait } from './sharePortrait'
import './register'

const MONTH_FORMAT = /^\d{4}-(0[1-9]|1[0-2])$/

const BLOCKS: { block: PortraitBlock; id: FormatjsIntl.Message['ids'] }[] = [
  { block: 'categories', id: 'portrait.show.categories' },
  { block: 'income', id: 'portrait.show.income' },
  { block: 'month', id: 'portrait.show.month' },
]

/** The month card (Retrato do mês): drawn on this device, shared or saved as a PNG. */
export function PortraitPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'portrait.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  const me = useMe().data

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {me && workspace ? (
        <PortraitScreen workspaceId={workspace.id} timezone={workspace.timezone} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function PortraitScreen({ workspaceId, timezone }: { workspaceId: string; timezone: string }) {
  const intl = useIntl()
  const [params] = useSearchParams()
  const now = new Date()
  const asked = params.get('mes')
  const month =
    asked && MONTH_FORMAT.test(asked) && asked >= MIN_MONTH && asked <= maxMonth(now, timezone)
      ? asked
      : currentMonth(now, timezone)
  const dashboard = useDashboard(workspaceId, month)
  const categories = useCategories(workspaceId, 'EXPENSE').data
  // The plan only decorates the card: while it loads, fails or is offline, the card is not Founder's.
  const plan = usePlan(workspaceId).data
  const founder = plan?.key === 'FAMILY' && plan.founder
  const theme = founder ? plan.theme : null

  const [hidden, setHidden] = useState<Set<PortraitBlock>>(new Set())
  const [status, setStatus] = useState<FormatjsIntl.Message['ids'] | null>(null)
  const [fontsReady, setFontsReady] = useState(0)
  const canvas = useRef<HTMLCanvasElement>(null)

  const data = useMemo(() => {
    if (!dashboard.data) return null
    const names = new Map((categories ?? []).map((category) => [category.id, category.name]))
    return buildPortrait(dashboard.data, names)
  }, [dashboard.data, categories])

  useEffect(() => {
    // The card draws again once Inter is there, so the PNG never carries the fallback font.
    void document.fonts?.load?.('600 40px "Inter Variable"').then(() => setFontsReady((n) => n + 1))
  }, [])

  useEffect(() => {
    const ctx = canvas.current?.getContext('2d')
    if (!ctx || !data) return
    drawPortrait(ctx, data, { hidden, theme, founder, tokens: readTokens() })
  }, [data, hidden, theme, founder, fontsReady])

  function toggle(block: PortraitBlock) {
    setHidden((current) => {
      const next = new Set(current)
      if (next.has(block)) next.delete(block)
      else next.add(block)
      return next
    })
  }

  async function share() {
    if (!canvas.current) return
    try {
      const result = await sharePortrait(canvas.current, month)
      setStatus(
        result === 'shared'
          ? 'portrait.shared'
          : result === 'downloaded'
            ? 'portrait.downloaded'
            : null,
      )
    } catch {
      setStatus('portrait.shareFailed')
    }
  }

  if (!data) {
    return (
      <p>
        <FormattedMessage id={dashboard.isError ? 'common.error' : 'common.loading'} />
      </p>
    )
  }

  const summary = data.empty
    ? intl.formatMessage({ id: 'portrait.summary.empty' })
    : [
        data.slices.map((slice) => `${slice.label} ${slice.percent}%`).join(', ') + '.',
        data.income
          ? intl.formatMessage(
              {
                id: data.income.kind === 'left' ? 'portrait.summary.left' : 'portrait.summary.over',
              },
              { percent: data.income.percent },
            )
          : '',
      ]
        .filter(Boolean)
        .join(' ')

  return (
    <>
      <canvas
        ref={canvas}
        width={PORTRAIT_WIDTH}
        height={PORTRAIT_HEIGHT}
        role="img"
        aria-label={intl.formatMessage({ id: 'portrait.preview' }, { month: data.month, summary })}
        className="h-auto w-full rounded-card border border-text-muted/30"
      />
      {data.empty && (
        <p className="rounded-card bg-surface p-4">
          <FormattedMessage id="portrait.empty" />
        </p>
      )}
      <fieldset className="flex flex-col gap-1">
        <legend className="font-semibold">
          <FormattedMessage id="portrait.show" />
        </legend>
        {BLOCKS.map(({ block, id }) => (
          <label key={block} className="flex min-h-12 items-center gap-3">
            <input
              type="checkbox"
              className="size-5"
              checked={!hidden.has(block)}
              onChange={() => toggle(block)}
            />
            <FormattedMessage id={id} />
          </label>
        ))}
      </fieldset>
      <p className="text-sm text-text-muted">
        <FormattedMessage id="portrait.privacy" />
      </p>
      <button
        type="button"
        disabled={data.empty}
        onClick={() => void share()}
        className="min-h-12 rounded-field bg-primary px-4 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
      >
        <FormattedMessage id="portrait.share" />
      </button>
      <p role="status" className="text-sm">
        {status && <FormattedMessage id={status} />}
      </p>
    </>
  )
}
