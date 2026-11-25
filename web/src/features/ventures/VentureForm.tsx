import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useIntl } from 'react-intl'
import { useNavigate } from 'react-router'
import { ApiError, type MeWorkspace } from '@/shared/api'
import {
  useUpdateVenture,
  type UpdateVentureRequest,
  type VentureType,
  type VentureView,
} from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { centsToApi, parseCents, typedAmount } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { FiguresPanel } from './FiguresPanel'
import { venturesMessages } from './messages.pt-BR'
import {
  parseAmount,
  parseUnits,
  useVentureSimulation,
  type DraftCost,
  type DraftProduct,
  type VentureDraft,
} from './useVentureSimulation'

registerMessages(venturesMessages)

const MAX_NAME_LENGTH = 40
const MAX_ITEMS = 20
const MAX_PRODUCTS = 10
const TYPES: readonly VentureType[] = [
  'FOOD',
  'DIGITAL_PRODUCT',
  'CONTENT',
  'RESALE',
  'SERVICE',
  'OTHER',
]

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
// Keeps a focused field above the fixed preview (up to 45vh) when the browser scrolls it into view.
const clearOfPanel = { scrollMarginBottom: '50vh' } as const
const field = `min-h-12 w-full rounded-field border border-primary bg-surface px-3 ${focus}`
const button = `min-h-12 rounded-field px-4 font-medium disabled:opacity-50 ${focus}`
const outline = `${button} border border-primary text-primary`

type VentureMessageId = keyof typeof venturesMessages
type CostList = 'initialItems' | 'fixedItems'

const money = (amount: string) => typedAmount(parseCents(amount))

function draftFrom(venture: VentureView): VentureDraft {
  const cost = (item: VentureView['initialItems'][number]): DraftCost => ({
    key: item.id,
    id: item.id,
    name: item.name,
    amount: money(item.amount.amount),
    isExample: item.isExample,
  })
  return {
    name: venture.name,
    type: venture.type,
    startedOn: venture.startedOn,
    initialItems: venture.initialItems.map(cost),
    fixedItems: venture.fixedItems.map(cost),
    products: venture.products.map((item): DraftProduct => ({
      key: item.id,
      id: item.id,
      name: item.name,
      unitPrice: money(item.unitPrice.amount),
      unitVariableCost: money(item.unitVariableCost.amount),
      plannedUnits: String(item.plannedUnitsMonth),
      isExample: item.isExample,
    })),
  }
}

interface VentureFormProps {
  workspace: MeWorkspace
  venture: VentureView
  /** Reloads the saved project: asked for after a conflict. The form keeps the draft until then. */
  onReload?: () => void
}

/**
 * The project's fields with the live figures beside them (below on a phone). The draft lives here
 * as typed; the API checks it again on save. A conflict keeps the draft and offers to reload.
 */
export function VentureForm({ workspace, venture, onReload }: VentureFormProps) {
  const intl = useIntl()
  const toast = useToast()
  const navigate = useNavigate()
  const online = useOnline()
  const uid = useId()
  const update = useUpdateVenture(workspace.id, venture.id)
  const [draft, setDraft] = useState(() => draftFrom(venture))
  // The version the person opened; a refresh of the saved project must not move it under the draft.
  const [version] = useState(venture.version)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<string>()
  const [conflict, setConflict] = useState(false)
  const focusId = useRef<string | undefined>(undefined)
  const setFocusId = (id: string) => {
    focusId.current = id
  }
  const counter = useRef(0)
  const simulation = useVentureSimulation(workspace.id, draft)
  const busy = update.isPending
  const hasExample = [...draft.initialItems, ...draft.fixedItems, ...draft.products].some(
    (each) => each.isExample,
  )

  // After the render that added, removed or flagged a field, move the focus where it belongs.
  useEffect(() => {
    if (focusId.current === undefined) return
    document.getElementById(focusId.current)?.focus()
    focusId.current = undefined
  })

  const idOf = (...parts: string[]) => `${uid}-${parts.join('-')}`
  const nextKey = () => `new-${(counter.current += 1)}`

  function edit(list: CostList, key: string, patch: Partial<DraftCost>) {
    setDraft((current) => ({
      ...current,
      [list]: current[list].map((row) =>
        row.key === key ? { ...row, ...patch, isExample: false } : row,
      ),
    }))
  }

  function editProduct(key: string, patch: Partial<DraftProduct>) {
    setDraft((current) => ({
      ...current,
      products: current.products.map((row) =>
        row.key === key ? { ...row, ...patch, isExample: false } : row,
      ),
    }))
  }

  function addCost(list: CostList) {
    const key = nextKey()
    setDraft((current) => ({
      ...current,
      [list]: [...current[list], { key, id: null, name: '', amount: '', isExample: false }],
    }))
    setFocusId(idOf(list, key, 'name'))
  }

  function addProduct() {
    const key = nextKey()
    setDraft((current) => ({
      ...current,
      products: [
        ...current.products,
        {
          key,
          id: null,
          name: '',
          unitPrice: '',
          unitVariableCost: '',
          plannedUnits: '',
          isExample: false,
        },
      ],
    }))
    setFocusId(idOf('products', key, 'name'))
  }

  function removeCost(list: CostList, key: string) {
    setDraft((current) => ({ ...current, [list]: current[list].filter((row) => row.key !== key) }))
    setFocusId(idOf(list, 'add'))
  }

  function removeProduct(key: string) {
    setDraft((current) => ({
      ...current,
      products: current.products.filter((row) => row.key !== key),
    }))
    setFocusId(idOf('products', 'add'))
  }

  /** The field errors of the draft, by the id of the field; empty when the draft can be saved. */
  function validate(): Record<string, string> {
    const found: Record<string, string> = {}
    const badName = (text: string) => text.trim() === '' || text.trim().length > MAX_NAME_LENGTH
    const nameText = intl.formatMessage({ id: 'ventures.form.errorName' })
    const amountText = intl.formatMessage({ id: 'ventures.form.errorAmount' })
    const positiveText = intl.formatMessage({ id: 'ventures.form.errorPositive' })
    const amountError = (text: string) => {
      const cents = parseAmount(text)
      return cents === undefined ? amountText : cents === 0n ? positiveText : undefined
    }
    if (badName(draft.name)) found[idOf('name')] = nameText
    for (const list of ['initialItems', 'fixedItems'] as const) {
      for (const row of draft[list]) {
        if (badName(row.name)) found[idOf(list, row.key, 'name')] = nameText
        const amountFound = amountError(row.amount)
        if (amountFound) found[idOf(list, row.key, 'amount')] = amountFound
      }
    }
    for (const row of draft.products) {
      if (badName(row.name)) found[idOf('products', row.key, 'name')] = nameText
      const priceFound = amountError(row.unitPrice)
      if (priceFound) found[idOf('products', row.key, 'price')] = priceFound
      if (parseAmount(row.unitVariableCost) === undefined) {
        found[idOf('products', row.key, 'cost')] = amountText
      }
      if (parseUnits(row.plannedUnits) === undefined) {
        found[idOf('products', row.key, 'units')] = intl.formatMessage({
          id: 'ventures.form.errorUnits',
        })
      }
    }
    return found
  }

  function bodyFrom(): UpdateVentureRequest {
    const cost = (row: DraftCost) => ({
      id: row.id,
      name: row.name.trim(),
      amount: centsToApi(parseAmount(row.amount) ?? 0n),
    })
    return {
      version,
      name: draft.name.trim(),
      type: draft.type,
      ...(draft.startedOn ? { startedOn: draft.startedOn } : {}),
      initialItems: draft.initialItems.map(cost),
      fixedItems: draft.fixedItems.map(cost),
      products: draft.products.map((row) => ({
        id: row.id,
        name: row.name.trim(),
        unitPrice: centsToApi(parseAmount(row.unitPrice) ?? 0n),
        unitVariableCost: centsToApi(parseAmount(row.unitVariableCost) ?? 0n),
        plannedUnitsMonth: parseUnits(row.plannedUnits) ?? 0,
      })),
    }
  }

  async function save() {
    if (!online || busy) return
    setFailure(undefined)
    const found = validate()
    setErrors(found)
    const first = Object.keys(found)[0]
    if (first !== undefined) {
      setFocusId(first)
      return
    }
    try {
      await update.mutateAsync(bodyFrom())
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setConflict(true)
      } else if (error instanceof ApiError && error.status === 422 && error.detail) {
        setFailure(error.detail)
      } else {
        console.error(error)
        setFailure(intl.formatMessage({ id: 'ventures.form.failed' }))
      }
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'ventures.form.saved' }) })
    void navigate(`/mais/projetos/${venture.id}`)
  }

  function Text(props: {
    id: string
    label: string
    value: string
    onChange: (value: string) => void
    numeric?: boolean
    maxLength?: number
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
          style={clearOfPanel}
        />
        {error && (
          <p id={`${props.id}-error`} className="text-sm">
            {error}
          </p>
        )}
      </div>
    )
  }

  const example = (
    <span className="self-start rounded-field border border-text-muted/40 px-2 py-1 text-xs text-text-muted">
      {intl.formatMessage({ id: 'ventures.form.example' })}
    </span>
  )

  function costSection(
    list: CostList,
    titleId: VentureMessageId,
    rowId: VentureMessageId,
    addId: VentureMessageId,
  ) {
    const rows = draft[list]
    return (
      <section className="flex flex-col gap-3" aria-labelledby={idOf(list, 'title')}>
        <h2 id={idOf(list, 'title')} className="text-lg font-semibold">
          {intl.formatMessage({ id: titleId })}
        </h2>
        {rows.map((row, index) => {
          const legend = intl.formatMessage({ id: rowId }, { n: index + 1 })
          return (
            <Row
              key={row.key}
              legend={legend}
              removeLabel={intl.formatMessage({ id: 'ventures.form.remove' }, { what: legend })}
              onRemove={() => removeCost(list, row.key)}
            >
              {row.isExample && example}
              {Text({
                id: idOf(list, row.key, 'name'),
                label: intl.formatMessage({ id: 'ventures.form.itemName' }),
                value: row.name,
                maxLength: MAX_NAME_LENGTH,
                onChange: (value) => edit(list, row.key, { name: value }),
              })}
              {Text({
                id: idOf(list, row.key, 'amount'),
                label: intl.formatMessage({ id: 'ventures.form.itemAmount' }),
                value: row.amount,
                numeric: true,
                onChange: (value) => edit(list, row.key, { amount: value }),
              })}
            </Row>
          )
        })}
        <button
          type="button"
          id={idOf(list, 'add')}
          disabled={rows.length >= MAX_ITEMS}
          onClick={() => addCost(list)}
          className={`${outline} self-start`}
          style={clearOfPanel}
        >
          {intl.formatMessage({ id: addId })}
        </button>
      </section>
    )
  }

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-4 p-6 pb-[50vh] lg:pb-6">
      <h1 className="text-2xl font-semibold">
        {intl.formatMessage({ id: 'ventures.edit.title' })}
      </h1>
      {hasExample && (
        <p role="note" className="rounded-card bg-surface p-3 text-sm">
          {intl.formatMessage({ id: 'ventures.form.exampleBanner' })}
        </p>
      )}
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        <form
          noValidate
          className="flex flex-col gap-6"
          onSubmit={(event) => {
            event.preventDefault()
            void save()
          }}
        >
          {Text({
            id: idOf('name'),
            label: intl.formatMessage({ id: 'ventures.form.name' }),
            value: draft.name,
            maxLength: MAX_NAME_LENGTH,
            onChange: (value) => setDraft((current) => ({ ...current, name: value })),
          })}
          <div className="flex flex-col gap-1">
            <label htmlFor={idOf('type')} className="font-medium">
              {intl.formatMessage({ id: 'ventures.form.type' })}
            </label>
            <select
              id={idOf('type')}
              value={draft.type}
              onChange={(event) =>
                setDraft((current) => ({ ...current, type: event.target.value as VentureType }))
              }
              className={field}
              style={clearOfPanel}
            >
              {TYPES.map((each) => (
                <option key={each} value={each}>
                  {intl.formatMessage({ id: `ventures.type.${each}` })}
                </option>
              ))}
            </select>
          </div>
          {venture.status !== 'PLANNING' && (
            <div className="flex flex-col gap-1">
              <label htmlFor={idOf('startedOn')} className="font-medium">
                {intl.formatMessage({ id: 'ventures.form.startedOn' })}
              </label>
              <input
                id={idOf('startedOn')}
                type="date"
                value={draft.startedOn ?? ''}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, startedOn: event.target.value || null }))
                }
                className={field}
                style={clearOfPanel}
              />
            </div>
          )}
          {costSection(
            'initialItems',
            'ventures.form.initialItems',
            'ventures.form.initialItem',
            'ventures.form.addInitial',
          )}
          {costSection(
            'fixedItems',
            'ventures.form.fixedItems',
            'ventures.form.fixedItem',
            'ventures.form.addFixed',
          )}
          <section className="flex flex-col gap-3" aria-labelledby={idOf('products', 'title')}>
            <h2 id={idOf('products', 'title')} className="text-lg font-semibold">
              {intl.formatMessage({ id: 'ventures.form.products' })}
            </h2>
            {draft.products.map((row, index) => {
              const legend = intl.formatMessage({ id: 'ventures.form.product' }, { n: index + 1 })
              return (
                <Row
                  key={row.key}
                  legend={legend}
                  removeLabel={intl.formatMessage({ id: 'ventures.form.remove' }, { what: legend })}
                  onRemove={() => removeProduct(row.key)}
                >
                  {row.isExample && example}
                  {Text({
                    id: idOf('products', row.key, 'name'),
                    label: intl.formatMessage({ id: 'ventures.form.itemName' }),
                    value: row.name,
                    maxLength: MAX_NAME_LENGTH,
                    onChange: (value) => editProduct(row.key, { name: value }),
                  })}
                  {Text({
                    id: idOf('products', row.key, 'price'),
                    label: intl.formatMessage({ id: 'ventures.form.productPrice' }),
                    value: row.unitPrice,
                    numeric: true,
                    onChange: (value) => editProduct(row.key, { unitPrice: value }),
                  })}
                  {Text({
                    id: idOf('products', row.key, 'cost'),
                    label: intl.formatMessage({ id: 'ventures.form.productCost' }),
                    value: row.unitVariableCost,
                    numeric: true,
                    onChange: (value) => editProduct(row.key, { unitVariableCost: value }),
                  })}
                  {Text({
                    id: idOf('products', row.key, 'units'),
                    label: intl.formatMessage({ id: 'ventures.form.productUnits' }),
                    value: row.plannedUnits,
                    numeric: true,
                    onChange: (value) => editProduct(row.key, { plannedUnits: value }),
                  })}
                </Row>
              )
            })}
            <button
              type="button"
              id={idOf('products', 'add')}
              disabled={draft.products.length >= MAX_PRODUCTS}
              onClick={addProduct}
              className={`${outline} self-start`}
              style={clearOfPanel}
            >
              {intl.formatMessage({ id: 'ventures.form.addProduct' })}
            </button>
          </section>
          {failure && (
            <p id={idOf('failure')} role="alert">
              {failure}
            </p>
          )}
          {!online && (
            <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.form.offline' })}</p>
          )}
          <button
            type="submit"
            disabled={!online || busy}
            aria-describedby={failure ? idOf('failure') : undefined}
            className={`${button} bg-primary text-on-primary`}
          >
            {intl.formatMessage({ id: 'ventures.form.save' })}
          </button>
        </form>
        <div className="fixed inset-x-0 bottom-0 z-30 max-h-[45vh] overflow-y-auto bg-surface p-3 shadow-lg lg:sticky lg:top-4 lg:max-h-none lg:bg-transparent lg:p-0 lg:shadow-none">
          <FiguresPanel
            {...(simulation.figures ? { figures: simulation.figures } : {})}
            currency={workspace.currency}
            offline={simulation.offline}
            pending={simulation.pending}
            error={simulation.error}
            productNames={draft.products.map((row) => row.name)}
          />
        </div>
      </div>
      <ConfirmDialog
        open={conflict}
        title={intl.formatMessage({ id: 'ventures.form.conflictTitle' })}
        body={intl.formatMessage({ id: 'ventures.form.conflictBody' })}
        confirmLabel={intl.formatMessage({ id: 'ventures.form.conflictReload' })}
        cancelLabel={intl.formatMessage({ id: 'ventures.form.conflictKeep' })}
        onConfirm={() => {
          setConflict(false)
          onReload?.()
        }}
        onCancel={() => setConflict(false)}
      />
    </main>
  )
}

interface RowProps {
  legend: string
  removeLabel: string
  onRemove: () => void
  children: ReactNode
}

function Row({ legend, removeLabel, onRemove, children }: RowProps) {
  const intl = useIntl()
  return (
    <fieldset className="flex flex-col gap-3 rounded-card border border-text-muted/30 p-4">
      <legend className="px-1 font-medium">{legend}</legend>
      {children}
      <button
        type="button"
        aria-label={removeLabel}
        onClick={onRemove}
        className={`${outline} self-start`}
        style={clearOfPanel}
      >
        {intl.formatMessage({ id: 'ventures.form.remove' }, { what: '' }).trim()}
      </button>
    </fieldset>
  )
}
