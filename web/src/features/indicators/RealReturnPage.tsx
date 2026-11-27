import { useId, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useCurrentWorkspace } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { PageTip } from '@/shared/help'
import { useDocumentTitle } from '@/shared/ui'
import { Disclaimer } from '@/shared/ui/Disclaimer'
import { SourceStamp } from '@/shared/ui/SourceStamp'
import { formatDecimal } from './format'
import { indicatorsMessages } from './messages.pt-BR'
import { formatInputRate, formatRate, isRateInRange, parseRate, realReturn } from './realReturn'
import { useIndicators } from './useIndicators'

// The text loads with this lazy screen, not with the app.
registerMessages(indicatorsMessages)

const input =
  'min-h-12 rounded-field border border-text-muted/30 bg-surface px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The rate as typed: readable and inside the accepted range, else null. */
function readRate(text: string): bigint | null {
  const rate = parseRate(text)
  return rate !== null && isRateInRange(rate) ? rate : null
}

interface RateFieldProps {
  label: string
  value: string
  onChange: (text: string) => void
  /** The field's rate, or null when it is empty, unreadable or out of range. */
  rate: bigint | null
}

function RateField({ label, value, onChange, rate }: RateFieldProps) {
  const errorId = useId()
  const invalid = value.trim() !== '' && rate === null
  return (
    <>
      <label className="flex flex-col gap-1">
        <span className="font-medium">{label}</span>
        <input
          type="text"
          inputMode="decimal"
          autoComplete="off"
          value={value}
          aria-invalid={invalid ? true : undefined}
          aria-describedby={invalid ? errorId : undefined}
          onChange={(event) => onChange(event.target.value)}
          className={input}
        />
      </label>
      {invalid ? (
        <span id={errorId} className="text-sm text-text-muted">
          <FormattedMessage id="indicators.calc.rangeError" />
        </span>
      ) : null}
    </>
  )
}

/** What a year's return is worth after inflation, with the arithmetic shown. */
export function RealReturnPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'indicators.calc.title' }))
  const indicators = useIndicators(useCurrentWorkspace()?.id)
  const ipca = indicators.data?.indicators.find((item) => item.code === 'IPCA_12M')
  const prefill = ipca?.value == null ? '' : formatDecimal(ipca.value)

  const [nominalText, setNominalText] = useState('')
  // Null until the person types; then their text wins over the prefill, even after a refetch.
  const [typedInflation, setTypedInflation] = useState<string | null>(null)
  const inflationText = typedInflation ?? prefill

  const nominal = readRate(nominalText)
  const inflation = readRate(inflationText)
  const real = nominal !== null && inflation !== null ? realReturn(nominal, inflation) : null

  return (
    <>
      <Disclaimer>
        <FormattedMessage id="indicators.disclaimer" />
      </Disclaimer>
      <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
        <h1 className="text-2xl font-semibold">
          <FormattedMessage id="indicators.calc.title" />
        </h1>
        <PageTip id="calculator" />
        <RateField
          label={intl.formatMessage({ id: 'indicators.calc.nominal' })}
          value={nominalText}
          onChange={setNominalText}
          rate={nominal}
        />
        <RateField
          label={intl.formatMessage({ id: 'indicators.calc.inflation' })}
          value={inflationText}
          onChange={setTypedInflation}
          rate={inflation}
        />
        {typedInflation === null && ipca !== undefined && prefill !== '' ? (
          <SourceStamp
            source={ipca.source.name}
            url={ipca.source.url}
            updatedAt={ipca.updatedAt}
            stale={ipca.stale}
          />
        ) : inflationText.trim() !== '' ? (
          <p className="text-sm text-text-muted">
            <FormattedMessage id="indicators.calc.userValue" />
          </p>
        ) : null}
        <output aria-live="polite" className="flex flex-col gap-1">
          {real !== null && nominal !== null && inflation !== null ? (
            <>
              <span className="text-3xl font-semibold">{`${formatRate(real)}%`}</span>
              {real < 0n ? (
                <span>
                  <FormattedMessage id="indicators.calc.below" />
                </span>
              ) : real === 0n ? (
                <span>
                  <FormattedMessage id="indicators.calc.equal" />
                </span>
              ) : null}
              <span className="text-sm text-text-muted">
                <FormattedMessage
                  id="indicators.calc.formula"
                  values={{
                    nominal: formatInputRate(nominal),
                    inflation: formatInputRate(inflation),
                    real: formatRate(real),
                  }}
                />
              </span>
            </>
          ) : null}
        </output>
      </main>
    </>
  )
}
