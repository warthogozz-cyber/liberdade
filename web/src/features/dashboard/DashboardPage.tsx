import { useMemo, type ReactNode } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link, useNavigate, useSearchParams } from 'react-router'
import {
  useCategories,
  useCurrentWorkspace,
  useDashboard,
  useMe,
  type MeWorkspace,
} from '@/shared/api'
import { MIN_MONTH, addMonths, currentMonth, maxMonth } from '@/shared/i18n'
import { formatBRL, parseCents } from '@/shared/money'
import { useOnline, usePendingTotals } from '@/shared/offline'
import { DataAsOf, useDocumentTitle } from '@/shared/ui'
import { MonthSwitcher } from '@/shared/ui/MonthSwitcher'
import { EChart } from './EChart'
import {
  CHART_PALETTE,
  OTHERS_COLOR,
  balanceLineOption,
  categorySlices,
  comparisonOption,
  donutOption,
} from './charts'
import './register'

const MONTH_FORMAT = /^\d{4}-(0[1-9]|1[0-2])$/

/** The month dashboard (wireframe 5): the totals, spending by category, against last month, and the balance. */
export function DashboardPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'dashboard.title' })
  useDocumentTitle(title)
  const me = useMe().data
  const workspace = useCurrentWorkspace()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {me && workspace ? (
        <DashboardScreen userId={me.user.id} workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function readAnimate(): boolean {
  return !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/** The axes take the current theme's text color. */
function readTextColor(): string {
  return (
    getComputedStyle(document.documentElement).getPropertyValue('--color-text').trim() || '#444444'
  )
}

const sectionClass = 'flex flex-col gap-3 rounded-card bg-surface p-4'
const rowClass =
  'flex min-h-12 items-center justify-between gap-2 rounded-field focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

function listLink(month: string, categoryId: string): string {
  return `/lancamentos?mes=${month}&categoria=${categoryId}`
}

function DashboardScreen({ userId, workspace }: { userId: string; workspace: MeWorkspace }) {
  const intl = useIntl()
  const navigate = useNavigate()
  const online = useOnline()
  const [params, setParams] = useSearchParams()
  const now = new Date()
  const thisMonth = currentMonth(now, workspace.timezone)
  const lastMonth = maxMonth(now, workspace.timezone)
  const asked = params.get('mes')
  const month =
    asked && MONTH_FORMAT.test(asked) && asked >= MIN_MONTH && asked <= lastMonth
      ? asked
      : thisMonth
  const dashboard = useDashboard(workspace.id, month)
  const categories = useCategories(workspace.id, 'EXPENSE').data
  const pending = usePendingTotals(userId, workspace.id, month)
  const data = dashboard.data

  const slices = useMemo(
    () =>
      data
        ? categorySlices(
            data.byCategory,
            categories ?? [],
            intl.formatMessage({ id: 'dashboard.others' }),
          )
        : [],
    [data, categories, intl],
  )
  const days = useMemo(
    () =>
      data?.dailyBalance.map(({ day, balance }) => ({
        day,
        balance: parseCents(balance.amount),
      })) ?? [],
    [data],
  )
  const monthLabels = useMemo(() => {
    const name = (value: string) =>
      intl.formatDate(`${value}-01T00:00:00Z`, { month: 'long', timeZone: 'UTC' })
    return { current: name(month), previous: name(addMonths(month, -1)) }
  }, [month, intl])
  const animate = readAnimate()
  const textColor = readTextColor()
  const options = useMemo(() => {
    const opts = { animate, textColor }
    return {
      donut: donutOption(slices, opts),
      comparison: comparisonOption(slices, monthLabels, opts),
      line: balanceLineOption(days, opts),
    }
  }, [slices, days, monthLabels, animate, textColor])

  function setMonth(next: string) {
    const merged = new URLSearchParams(params)
    merged.set('mes', next)
    setParams(merged)
  }

  let content: ReactNode
  if (data) {
    // The queue only counts in the current month; the others are what the server says.
    const queued = month === thisMonth ? pending : null
    const queuedIncome = queued?.monthIncome ?? 0n
    const queuedExpenses = queued?.monthExpenses ?? 0n
    const queuedBalance = queued?.balanceDelta ?? 0n
    const hasPending = queuedIncome !== 0n || queuedExpenses !== 0n || queuedBalance !== 0n
    const total = slices.reduce((sum, slice) => sum + slice.current, 0n)
    const share = (cents: bigint) =>
      intl.formatNumber(total === 0n ? 0 : Number((cents * 10000n) / total) / 10000, {
        style: 'percent',
        maximumFractionDigits: 1,
      })
    const date = (day: string) =>
      intl.formatDate(`${day}T00:00:00Z`, { day: '2-digit', month: '2-digit', timeZone: 'UTC' })
    const byCategoryLabel = intl.formatMessage({ id: 'dashboard.byCategory' })
    const vsPrevious = (
      <FormattedMessage id="dashboard.vsPrevious" values={{ month: monthLabels.previous }} />
    )

    content = (
      <>
        <dl className="grid grid-cols-2 gap-3">
          <Card id="dashboard.balance" cents={parseCents(data.balance.amount) + queuedBalance} />
          <Card id="dashboard.income" cents={parseCents(data.income.amount) + queuedIncome} />
          <Card id="dashboard.expenses" cents={parseCents(data.expenses.amount) + queuedExpenses} />
          <Card
            id="dashboard.result"
            cents={parseCents(data.result.amount) + queuedIncome - queuedExpenses}
          />
        </dl>
        {hasPending && (
          <p className="text-sm text-text-muted">
            <FormattedMessage id="dashboard.pendingNote" />
          </p>
        )}
        {!online && (
          <p className="text-sm text-text-muted">
            <DataAsOf updatedAt={dashboard.dataUpdatedAt} timeZone={workspace.timezone} />
          </p>
        )}

        {total > 0n ? (
          <>
            <section aria-labelledby="by-category" className={sectionClass}>
              <h2 id="by-category" className="font-semibold">
                {byCategoryLabel}
              </h2>
              <EChart
                option={options.donut}
                height={200}
                onItemClick={(id) => void navigate(listLink(month, id))}
              />
              <ul aria-label={byCategoryLabel} className="flex flex-col">
                {slices.map((slice) => {
                  const row = (
                    <>
                      <span className="flex items-center gap-2">
                        <Dot color={slice.color} />
                        {slice.label}
                      </span>
                      <span className="tabular-nums">
                        {formatBRL(slice.current)}{' '}
                        <span className="text-text-muted">{share(slice.current)}</span>
                      </span>
                    </>
                  )
                  return (
                    <li key={slice.id ?? 'others'}>
                      {slice.id === null ? (
                        <div className={rowClass}>{row}</div>
                      ) : (
                        <Link to={listLink(month, slice.id)} className={rowClass}>
                          {row}
                        </Link>
                      )}
                    </li>
                  )
                })}
              </ul>
              <TableToggle>
                <caption className="pb-2 text-left font-medium">{byCategoryLabel}</caption>
                <thead>
                  <tr>
                    <th scope="col">
                      <FormattedMessage id="dashboard.table.category" />
                    </th>
                    <th scope="col">
                      <FormattedMessage id="dashboard.table.amount" />
                    </th>
                    <th scope="col">
                      <FormattedMessage id="dashboard.table.share" />
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {slices.map((slice) => (
                    <tr key={slice.id ?? 'others'}>
                      <th scope="row" className="font-normal">
                        {slice.label}
                      </th>
                      <td className="tabular-nums">{formatBRL(slice.current)}</td>
                      <td className="tabular-nums">{share(slice.current)}</td>
                    </tr>
                  ))}
                </tbody>
              </TableToggle>
            </section>

            <section aria-labelledby="vs-previous" className={sectionClass}>
              <h2 id="vs-previous" className="font-semibold">
                {vsPrevious}
              </h2>
              <ul className="flex gap-4 text-sm">
                <li className="flex items-center gap-2">
                  <Dot color={OTHERS_COLOR} />
                  {monthLabels.previous}
                </li>
                <li className="flex items-center gap-2">
                  <Dot color={CHART_PALETTE[0]} />
                  {monthLabels.current}
                </li>
              </ul>
              <EChart option={options.comparison} height={240} />
              <TableToggle>
                <caption className="pb-2 text-left font-medium">{vsPrevious}</caption>
                <thead>
                  <tr>
                    <th scope="col">
                      <FormattedMessage id="dashboard.table.category" />
                    </th>
                    <th scope="col">{monthLabels.current}</th>
                    <th scope="col">{monthLabels.previous}</th>
                  </tr>
                </thead>
                <tbody>
                  {slices.map((slice) => (
                    <tr key={slice.id ?? 'others'}>
                      <th scope="row" className="font-normal">
                        {slice.label}
                      </th>
                      <td className="tabular-nums">{formatBRL(slice.current)}</td>
                      <td className="tabular-nums">{formatBRL(slice.previous)}</td>
                    </tr>
                  ))}
                </tbody>
              </TableToggle>
            </section>
          </>
        ) : (
          <p className="rounded-card bg-surface p-4">
            <FormattedMessage id="dashboard.noExpenses" />
          </p>
        )}

        <section aria-labelledby="daily-balance" className={sectionClass}>
          <h2 id="daily-balance" className="font-semibold">
            <FormattedMessage id="dashboard.dailyBalance" />
          </h2>
          <EChart option={options.line} height={220} />
          <TableToggle>
            <caption className="pb-2 text-left font-medium">
              <FormattedMessage id="dashboard.dailyBalance" />
            </caption>
            <thead>
              <tr>
                <th scope="col">
                  <FormattedMessage id="dashboard.table.day" />
                </th>
                <th scope="col">
                  <FormattedMessage id="dashboard.table.balance" />
                </th>
              </tr>
            </thead>
            <tbody>
              {days.map(({ day, balance }) => (
                <tr key={day}>
                  <th scope="row" className="font-normal">
                    {date(day)}
                  </th>
                  <td className="tabular-nums">{formatBRL(balance)}</td>
                </tr>
              ))}
            </tbody>
          </TableToggle>
        </section>
      </>
    )
  } else if (!online || dashboard.fetchStatus === 'paused') {
    content = (
      <p className="rounded-card bg-surface p-4">
        <FormattedMessage id="home.offlineNoData" />
      </p>
    )
  } else if (dashboard.isError) {
    content = (
      <div className="flex flex-col items-start gap-2">
        <p>
          <FormattedMessage id="common.error" />
        </p>
        <button
          type="button"
          onClick={() => void dashboard.refetch()}
          className="min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <FormattedMessage id="common.retry" />
        </button>
      </div>
    )
  } else {
    content = (
      <p>
        <FormattedMessage id="common.loading" />
      </p>
    )
  }

  return (
    <>
      <MonthSwitcher month={month} onChange={setMonth} max={lastMonth} />
      {content}
    </>
  )
}

function Card({ id, cents }: { id: FormatjsIntl.Message['ids']; cents: bigint }) {
  return (
    <div className="rounded-card bg-surface p-4">
      <dt className="text-sm text-text-muted">
        <FormattedMessage id={id} />
      </dt>
      <dd className="text-lg font-semibold tabular-nums">{formatBRL(cents)}</dd>
    </div>
  )
}

/** A color dot: decoration only, the name always sits beside it. */
function Dot({ color }: { color: string }) {
  return (
    <span
      aria-hidden="true"
      className="size-3 shrink-0 rounded-full"
      style={{ backgroundColor: color }}
    />
  )
}

/** "Ver tabela": the chart's data as a table, which is what a screen reader reads. */
function TableToggle({ children }: { children: ReactNode }) {
  return (
    <details>
      <summary className="flex min-h-12 cursor-pointer items-center font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
        <FormattedMessage id="dashboard.showTable" />
      </summary>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">{children}</table>
      </div>
    </details>
  )
}
