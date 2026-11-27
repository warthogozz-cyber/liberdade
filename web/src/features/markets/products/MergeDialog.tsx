import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { Sheet } from '@/shared/ui/Sheet'
import { marketsMessages } from '../messages.pt-BR'
import { useMergeProducts } from './useProducts'

registerMessages(marketsMessages)

export interface ProductName {
  id: string
  name: string
}

interface MergeDialogProps {
  ws: string
  a: ProductName
  b: ProductName
  onClose: () => void
  onMerged: () => void
}

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button = `min-h-12 rounded-field px-4 font-medium ${focus}`
const ERROR_BY_CODE = {
  'product.merge_gtin_conflict': 'markets.products.merge.gtin_conflict',
  'product.merge_same': 'markets.products.merge.same',
  'product.not_found': 'markets.products.merge.not_found',
} as const
type MergeErrorId =
  (typeof ERROR_BY_CODE)[keyof typeof ERROR_BY_CODE] | 'markets.products.merge.failed'

/** Asks which name stays before two products become one; there is no way back, and the text says so. */
export function MergeDialog({ ws, a, b, onClose, onMerged }: MergeDialogProps) {
  const intl = useIntl()
  const merge = useMergeProducts(ws)
  const [keepId, setKeepId] = useState(a.id)
  const kept = keepId === a.id ? a : b
  const removed = keepId === a.id ? b : a
  const failure = merge.error
  const known =
    failure instanceof ApiError
      ? (ERROR_BY_CODE as Record<string, MergeErrorId>)[failure.code]
      : undefined
  const errorId: MergeErrorId = known ?? 'markets.products.merge.failed'

  return (
    <Sheet
      open
      onOpenChange={(open) => !open && onClose()}
      title={<FormattedMessage id="markets.products.merge.title" />}
    >
      <div className="flex flex-col gap-4">
        <p>
          <FormattedMessage
            id="markets.products.merge.body"
            values={{ kept: kept.name, removed: removed.name }}
          />
        </p>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 font-medium">
            <FormattedMessage id="markets.products.merge.which" />
          </legend>
          {[a, b].map((each) => (
            <label
              key={each.id}
              className="flex min-h-12 items-center gap-3 rounded-field border border-border px-3"
            >
              <input
                type="radio"
                name="keep"
                checked={keepId === each.id}
                onChange={() => setKeepId(each.id)}
                className="size-5"
              />
              {each.name}
            </label>
          ))}
        </fieldset>
        {merge.isError && (
          <p role="alert" className="text-danger">
            {intl.formatMessage({ id: errorId })}
          </p>
        )}
        <div className="flex flex-col gap-2">
          <button
            type="button"
            disabled={merge.isPending}
            onClick={() =>
              merge.mutate({ keepId: kept.id, removeId: removed.id }, { onSuccess: onMerged })
            }
            className={`${button} bg-primary text-on-primary disabled:opacity-50`}
          >
            <FormattedMessage id="markets.products.merge.confirm" />
          </button>
          <button
            type="button"
            onClick={onClose}
            className={`${button} border border-primary text-primary`}
          >
            <FormattedMessage id="markets.products.merge.cancel" />
          </button>
        </div>
      </div>
    </Sheet>
  )
}
