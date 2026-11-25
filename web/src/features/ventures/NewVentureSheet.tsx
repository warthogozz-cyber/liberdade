import { useState } from 'react'
import { useIntl } from 'react-intl'
import { useNavigate } from 'react-router'
import { ApiError, useFormatMoney } from '@/shared/api'
import {
  useCreateVenture,
  type CreateVentureRequest,
  type TemplateView,
  type VentureType,
} from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { Sheet } from '@/shared/ui/Sheet'
import { paybackText } from './figuresText'
import { venturesMessages } from './messages.pt-BR'

registerMessages(venturesMessages)

const MAX_NAME_LENGTH = 40
const TYPES: readonly VentureType[] = [
  'FOOD',
  'DIGITAL_PRODUCT',
  'CONTENT',
  'RESALE',
  'SERVICE',
  'OTHER',
]

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const field = `min-h-12 w-full rounded-field border border-primary bg-surface px-3 ${focus}`

interface NewVentureSheetProps {
  workspaceId: string
  /** The template to start from; without it, a blank project that asks for a name and a type. */
  template?: TemplateView
  onClose: () => void
}

/** Starts a project: from a template, with its example and a suggested name, or blank. Needs the connection. */
export function NewVentureSheet({ workspaceId, template, onClose }: NewVentureSheetProps) {
  const intl = useIntl()
  const navigate = useNavigate()
  const online = useOnline()
  const formatMoney = useFormatMoney()
  const create = useCreateVenture(workspaceId)
  const [name, setName] = useState(template?.name ?? '')
  const [type, setType] = useState<VentureType>('OTHER')
  const [error, setError] = useState<string>()
  const invalid = error === intl.formatMessage({ id: 'ventures.sheet.invalidName' })

  async function submit() {
    const trimmed = name.trim()
    if (trimmed === '' || trimmed.length > MAX_NAME_LENGTH) {
      setError(intl.formatMessage({ id: 'ventures.sheet.invalidName' }))
      return
    }
    if (!online || create.isPending) return
    setError(undefined)
    const body: CreateVentureRequest = template
      ? { name: trimmed, templateKey: template.key }
      : { name: trimmed, type }
    try {
      const venture = await create.mutateAsync(body)
      void navigate(`/mais/projetos/${venture.id}/editar`)
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === 'venture.limit-reached') {
        setError(failure.detail ?? intl.formatMessage({ id: 'ventures.sheet.failed' }))
      } else {
        console.error(failure)
        setError(intl.formatMessage({ id: 'ventures.sheet.failed' }))
      }
    }
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({
        id: template ? 'ventures.sheet.titleTemplate' : 'ventures.sheet.titleBlank',
      })}
    >
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        {template && (
          <section className="flex flex-col gap-2">
            <p className="text-sm text-text-muted">
              {intl.formatMessage({ id: 'ventures.sheet.example' })}
            </p>
            <dl className="flex flex-col gap-1">
              <Row label={intl.formatMessage({ id: 'ventures.sheet.investment' })}>
                {formatMoney(parseCents(template.figures.initialInvestment.amount))}
              </Row>
              <Row label={intl.formatMessage({ id: 'ventures.profit' })}>
                {formatMoney(parseCents(template.figures.monthlyProfit.amount))}
              </Row>
              <Row label={intl.formatMessage({ id: 'ventures.payback' })}>
                {paybackText(template.figures)}
              </Row>
            </dl>
          </section>
        )}
        <label className="flex flex-col gap-1">
          <span className="font-medium">{intl.formatMessage({ id: 'ventures.sheet.name' })}</span>
          <input
            value={name}
            maxLength={MAX_NAME_LENGTH}
            autoComplete="off"
            aria-invalid={invalid}
            aria-describedby={error ? 'new-venture-error' : undefined}
            onChange={(event) => setName(event.target.value)}
            className={field}
          />
        </label>
        {!template && (
          <label className="flex flex-col gap-1">
            <span className="font-medium">{intl.formatMessage({ id: 'ventures.sheet.type' })}</span>
            <select
              value={type}
              onChange={(event) => setType(event.target.value as VentureType)}
              className={field}
            >
              {TYPES.map((each) => (
                <option key={each} value={each}>
                  {intl.formatMessage({ id: `ventures.type.${each}` })}
                </option>
              ))}
            </select>
          </label>
        )}
        {error && (
          <p id="new-venture-error" role="alert">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.sheet.offline' })}</p>
        )}
        <button
          type="submit"
          disabled={!online || create.isPending}
          className={`min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-50 ${focus}`}
        >
          {intl.formatMessage({ id: 'ventures.sheet.create' })}
        </button>
      </form>
    </Sheet>
  )
}

function Row({ label, children }: { label: string; children: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-text-muted">{label}</dt>
      <dd className="tabular-nums">{children}</dd>
    </div>
  )
}
