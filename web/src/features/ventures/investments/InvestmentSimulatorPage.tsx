import { useEffect, useId, useRef, useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, useCurrentWorkspace, useFormatMoney } from '@/shared/api'
import {
  useInvestmentSimulation,
  useSimulatorIndicators,
  type InvestmentSimulationRequest,
} from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { centsToApi, MAX_CENTS, parseCents, parseTypedAmount } from '@/shared/money'
import { useModuleEnabled } from '@/shared/modules'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { Disclaimer } from '@/shared/ui/Disclaimer'
import { SourceStamp } from '@/shared/ui/SourceStamp'
import { venturesMessages } from '../messages.pt-BR'

registerMessages(venturesMessages)

const MAX_SCENARIOS = 3
const MAX_NAME_LENGTH = 30
const MAX_YEARS = 50

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = `min-h-12 w-full rounded-field border border-primary bg-surface px-3 ${focus}`
const button = `min-h-12 rounded-field px-4 font-medium disabled:opacity-50 ${focus}`
const outline = `${button} border border-primary text-primary`

/** The Selic target and the CDI, the two scenarios the Indicators module can prefill. */
const PREFILLED = [
  { code: 'SELIC_TARGET', name: 'Selic' },
  { code: 'CDI', name: 'CDI' },
] as const

interface Indicator {
  value: string | null
  source: { name: string; url?: string | null }
  updatedAt: string | null
  stale: boolean
}

interface Scenario {
  key: string
  name: string
  rate: string
  /** The indicator the rate still comes from; null once the person typed their own. */
  stamp: Indicator | null
}

/** An amount typed with the family's separators: empty or zero is zero, else up to two decimals. */
function readAmount(text: string): bigint | undefined {
  if (text.trim() === '' || /^0+(,0{1,2})?$/.test(text.trim())) return 0n
  return parseTypedAmount(text)
}

/** A yearly rate from 0 to 100 with up to two decimals, as the API's dot-decimal text. */
function readRate(text: string): string | undefined {
  const clean = text.trim()
  if (!/^\d{1,3}(,\d{1,2})?$/.test(clean)) return undefined
  const dotted = clean.replace(',', '.')
  return Number(dotted) <= 100 ? dotted : undefined
}

function readYears(text: string): number | undefined {
  const clean = text.trim()
  if (!/^\d{1,2}$/.test(clean)) return undefined
  const years = Number(clean)
  return years >= 1 && years <= MAX_YEARS ? years : undefined
}

/** What a monthly saving grows to at constant yearly rates the person types, computed by the API. */
export function InvestmentSimulatorPage() {
  const intl = useIntl()
  const uid = useId()
  const online = useOnline()
  const formatMoney = useFormatMoney()
  const workspace = useCurrentWorkspace()
  const indicatorsOn = useModuleEnabled('indicators')
  const indicators = useSimulatorIndicators(indicatorsOn ? workspace?.id : undefined)
  const simulation = useInvestmentSimulation(workspace?.id ?? '')
  useDocumentTitle(intl.formatMessage({ id: 'ventures.sim.title' }))

  const [initial, setInitial] = useState('')
  const [monthly, setMonthly] = useState('')
  const [years, setYears] = useState('')
  // Null until the person changes a scenario; until then the rows follow the indicators.
  const [typed, setTyped] = useState<Scenario[] | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<string>()
  const counter = useRef(0)
  const focusId = useRef<string | undefined>(undefined)

  const prefilled: Scenario[] = indicatorsOn
    ? PREFILLED.flatMap(({ code, name }) => {
        const found = indicators.data?.indicators.find((item) => item.code === code)
        return found?.value == null
          ? []
          : [{ key: code, name, rate: found.value.replace('.', ','), stamp: found }]
      })
    : []
  const scenarios: Scenario[] =
    typed ??
    (prefilled.length > 0 ? prefilled : [{ key: 'new-0', name: '', rate: '', stamp: null }])

  useEffect(() => {
    if (focusId.current === undefined) return
    document.getElementById(focusId.current)?.focus()
    focusId.current = undefined
  })

  const idOf = (...parts: string[]) => `${uid}-${parts.join('-')}`
  const change = (next: (rows: Scenario[]) => Scenario[]) => setTyped(next(scenarios))
  const edit = (key: string, patch: Partial<Scenario>) =>
    change((rows) => rows.map((row) => (row.key === key ? { ...row, ...patch } : row)))

  function add() {
    const key = `new-${(counter.current += 1)}`
    change((rows) => [...rows, { key, name: '', rate: '', stamp: null }])
    focusId.current = idOf(key, 'name')
  }

  function remove(key: string) {
    change((rows) => rows.filter((row) => row.key !== key))
    focusId.current = idOf('add')
  }

  async function submit() {
    if (!online || simulation.isPending) return
    setFailure(undefined)
    const found: Record<string, string> = {}
    const amount = intl.formatMessage({ id: 'ventures.sim.errorAmount' })
    const initialCents = readAmount(initial)
    const monthlyCents = readAmount(monthly)
    const yearCount = readYears(years)
    if (initialCents === undefined || initialCents > MAX_CENTS) found[idOf('initial')] = amount
    if (monthlyCents === undefined || monthlyCents > MAX_CENTS) found[idOf('monthly')] = amount
    if (yearCount === undefined) {
      found[idOf('years')] = intl.formatMessage({ id: 'ventures.sim.errorYears' })
    }
    const body = scenarios.map((row) => {
      const name = row.name.trim()
      const annualPercent = readRate(row.rate)
      if (name === '' || name.length > MAX_NAME_LENGTH) {
        found[idOf(row.key, 'name')] = intl.formatMessage({ id: 'ventures.sim.errorName' })
      }
      if (annualPercent === undefined) {
        found[idOf(row.key, 'rate')] = intl.formatMessage({ id: 'ventures.sim.errorRate' })
      }
      return { name, annualPercent: annualPercent ?? '' }
    })
    setErrors(found)
    const first = Object.keys(found)[0]
    if (first !== undefined) {
      focusId.current = first
      return
    }
    const request: InvestmentSimulationRequest = {
      initialAmount: centsToApi(initialCents ?? 0n),
      monthlyContribution: centsToApi(monthlyCents ?? 0n),
      years: yearCount ?? 1,
      scenarios: body,
    }
    try {
      await simulation.mutateAsync(request)
    } catch (error) {
      if (error instanceof ApiError && error.status === 422 && error.detail) {
        setFailure(error.detail)
      } else {
        console.error(error)
        setFailure(intl.formatMessage({ id: 'ventures.sim.failed' }))
      }
    }
  }

  function Text(props: {
    id: string
    label: string
    value: string
    onChange: (value: string) => void
    maxLength?: number
    numeric?: boolean
  }) {
    const error = errors[props.id]
    return (
      <div className="flex flex-col gap-1">
        <label htmlFor={props.id} className="font-medium">
          {props.label}
        </label>
        <input
          id={props.id}
          value={props.value}
          autoComplete="off"
          inputMode={props.numeric ? 'decimal' : undefined}
          maxLength={props.maxLength}
          aria-invalid={error !== undefined}
          aria-describedby={error ? `${props.id}-error` : undefined}
          onChange={(event) => props.onChange(event.target.value)}
          className={field}
        />
        {error && (
          <p id={`${props.id}-error`} className="text-sm">
            {error}
          </p>
        )}
      </div>
    )
  }

  const money = (amount: string) => formatMoney(parseCents(amount))
  const result = simulation.data

  return (
    <>
      <Disclaimer>{intl.formatMessage({ id: 'ventures.invest.disclaimer' })}</Disclaimer>
      <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
        <h1 className="text-2xl font-semibold">
          {intl.formatMessage({ id: 'ventures.sim.title' })}
        </h1>
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            void submit()
          }}
        >
          {Text({
            id: idOf('initial'),
            label: intl.formatMessage({ id: 'ventures.sim.initial' }),
            value: initial,
            numeric: true,
            onChange: setInitial,
          })}
          {Text({
            id: idOf('monthly'),
            label: intl.formatMessage({ id: 'ventures.sim.monthly' }),
            value: monthly,
            numeric: true,
            onChange: setMonthly,
          })}
          {Text({
            id: idOf('years'),
            label: intl.formatMessage({ id: 'ventures.sim.years' }),
            value: years,
            numeric: true,
            onChange: setYears,
          })}
          {scenarios.map((row, index) => {
            const n = index + 1
            return (
              <fieldset
                key={row.key}
                className="flex flex-col gap-3 rounded-card border border-text-muted/30 p-4"
              >
                <legend className="px-1 font-medium">
                  {intl.formatMessage({ id: 'ventures.sim.scenario' }, { n })}
                </legend>
                {Text({
                  id: idOf(row.key, 'name'),
                  label: intl.formatMessage({ id: 'ventures.sim.scenarioName' }, { n }),
                  value: row.name,
                  maxLength: MAX_NAME_LENGTH,
                  onChange: (value) => edit(row.key, { name: value }),
                })}
                {Text({
                  id: idOf(row.key, 'rate'),
                  label: intl.formatMessage({ id: 'ventures.sim.scenarioRate' }, { n }),
                  value: row.rate,
                  numeric: true,
                  onChange: (value) => edit(row.key, { rate: value, stamp: null }),
                })}
                {row.stamp ? (
                  <SourceStamp
                    source={row.stamp.source.name}
                    url={row.stamp.source.url ?? null}
                    updatedAt={row.stamp.updatedAt}
                    stale={row.stamp.stale}
                  />
                ) : row.rate.trim() !== '' ? (
                  <p className="text-sm text-text-muted">
                    {intl.formatMessage({ id: 'ventures.sim.userValue' })}
                  </p>
                ) : null}
                {scenarios.length > 1 && (
                  <button
                    type="button"
                    onClick={() => remove(row.key)}
                    className={`${outline} self-start`}
                  >
                    {intl.formatMessage({ id: 'ventures.sim.remove' }, { n })}
                  </button>
                )}
              </fieldset>
            )
          })}
          {scenarios.length < MAX_SCENARIOS && (
            <button
              type="button"
              id={idOf('add')}
              onClick={add}
              className={`${outline} self-start`}
            >
              {intl.formatMessage({ id: 'ventures.sim.add' })}
            </button>
          )}
          {failure && <p role="alert">{failure}</p>}
          {!online && (
            <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.sim.offline' })}</p>
          )}
          <button
            type="submit"
            disabled={!online || simulation.isPending}
            className={`${button} bg-primary text-on-primary`}
          >
            {intl.formatMessage({ id: 'ventures.sim.submit' })}
          </button>
        </form>
        <div aria-live="polite" className="flex flex-col gap-3">
          {result && (
            <>
              <h2 className="text-lg font-semibold">
                {intl.formatMessage({ id: 'ventures.sim.result' })}
              </h2>
              {result.scenarios.map((each, index) => (
                <section
                  key={`${each.name}-${index}`}
                  aria-label={each.name}
                  className="flex flex-col gap-1 rounded-card bg-surface p-4"
                >
                  <h3 className="font-medium">{each.name}</h3>
                  <p>
                    {intl.formatMessage({ id: 'ventures.sim.finalBalance' })}
                    {': '}
                    {money(each.finalBalance.amount)}
                  </p>
                  <p>
                    {intl.formatMessage({ id: 'ventures.sim.contributed' })}
                    {': '}
                    {money(each.totalContributed.amount)}
                  </p>
                  <p>
                    {intl.formatMessage({ id: 'ventures.sim.earnings' })}
                    {': '}
                    {money(each.earnings.amount)}
                  </p>
                </section>
              ))}
              <p className="text-sm text-text-muted">
                {intl.formatMessage({ id: 'ventures.sim.note' })}
              </p>
            </>
          )}
        </div>
      </main>
    </>
  )
}
