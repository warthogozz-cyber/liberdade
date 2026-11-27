import { useId, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { ApiError, useCurrentWorkspace } from '@/shared/api'
import type { ShoppingListItem } from '@/shared/api/markets'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { marketsMessages } from '../messages.pt-BR'
import { ProductPicker } from '../receipt/ProductPicker'
import { ListItemRow } from './ListItemRow'
import {
  useAddListItem,
  useClearChecked,
  useRemoveListItem,
  useReorderList,
  useShoppingList,
  useUpdateListItem,
} from './useShoppingList'
import { PageTip } from '@/shared/help'

registerMessages(marketsMessages)

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const outline = `flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary ${focus}`

type Notice =
  | 'stale'
  | 'changeFailed'
  | 'removeFailed'
  | 'clearFailed'
  | 'addFailed'
  | 'quantityTooLarge'
  | 'full'

/** Refusals that trying again will not fix, so the notice says why instead of "try again". */
const ADD_REFUSALS: Record<string, Notice> = {
  'list.quantity_too_large': 'quantityTooLarge',
  'list.full': 'full',
}

/** The family's shopping list: what is missing at home, with the last price at each market. */
export function ShoppingListPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'markets.list.title' }))
  const pickedId = useId()
  const workspace = useCurrentWorkspace()
  const ws = workspace?.id
  // Everyone but the viewer adds, marks, changes the quantity and the order; only the owner and the
  // adults take items off (the API refuses the others).
  const canEdit =
    workspace?.role === 'OWNER' || workspace?.role === 'ADULT' || workspace?.role === 'YOUTH'
  const canRemove = workspace?.role === 'OWNER' || workspace?.role === 'ADULT'
  const list = useShoppingList(ws)
  const add = useAddListItem(ws ?? '')
  const update = useUpdateListItem(ws ?? '')
  const reorder = useReorderList(ws ?? '')
  const remove = useRemoveListItem(ws ?? '')
  const clear = useClearChecked(ws ?? '')
  const [search, setSearch] = useState('')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [confirmingClear, setConfirmingClear] = useState(false)

  const items = [...(list.data ?? [])].sort((a, b) => a.position - b.position)
  const open = items.filter((each) => !each.checked)
  const picked = items.filter((each) => each.checked)

  const failed = () => setNotice('changeFailed')

  function move(item: ShoppingListItem, direction: 'up' | 'down') {
    const neighbor = open[open.indexOf(item) + (direction === 'up' ? -1 : 1)]
    if (neighbor === undefined) return
    // The whole list is sent: the two swap places and every other item stays where it is.
    const ids = items.map((each) => each.id)
    const from = ids.indexOf(item.id)
    const to = ids.indexOf(neighbor.id)
    ids[from] = neighbor.id
    ids[to] = item.id
    setNotice(null)
    reorder.mutate(ids, {
      onError: (error) =>
        setNotice(
          error instanceof ApiError && error.code === 'list.order_stale' ? 'stale' : 'changeFailed',
        ),
    })
  }

  const row = (item: ShoppingListItem) => (
    <ListItemRow
      key={item.id}
      item={item}
      canEdit={canEdit}
      canRemove={canRemove}
      isFirst={open[0] === item}
      isLast={open[open.length - 1] === item}
      onCheck={(checked) => {
        setNotice(null)
        update.mutate({ id: item.id, body: { checked, quantity: null } }, { onError: failed })
      }}
      onQuantity={(quantity) => {
        setNotice(null)
        update.mutate({ id: item.id, body: { quantity, checked: null } }, { onError: failed })
      }}
      onMove={(direction) => move(item, direction)}
      onRemove={() => {
        setNotice(null)
        remove.mutate(item.id, { onError: () => setNotice('removeFailed') })
      }}
    />
  )

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <Link to="/mais/feira" className={`${outline} self-start`}>
        <FormattedMessage id="markets.list.back" />
      </Link>
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="markets.list.title" />
      </h1>
      <PageTip id="shopping-list" />

      <Link to="/mais/feira/comparar" className={`${outline} self-start`}>
        <FormattedMessage id="markets.compare.link" />
      </Link>

      {canEdit && ws !== undefined && (
        <section className="flex flex-col gap-2">
          <h2 className="font-medium">
            <FormattedMessage id="markets.list.add" />
          </h2>
          <ProductPicker
            ws={ws}
            value={null}
            text={search}
            onTextChange={setSearch}
            onChange={(choice) => {
              if (choice === null) return
              setNotice(null)
              add.mutate(
                choice.kind === 'existing'
                  ? { productId: choice.id, newProductName: null, quantity: null }
                  : { productId: null, newProductName: choice.name, quantity: null },
                {
                  onError: (error) =>
                    setNotice(
                      (error instanceof ApiError ? ADD_REFUSALS[error.code] : undefined) ??
                        'addFailed',
                    ),
                },
              )
              setSearch('')
            }}
          />
        </section>
      )}

      {notice !== null && (
        <p role="alert" className="text-danger">
          <FormattedMessage id={`markets.list.${notice}`} />
        </p>
      )}

      {list.isError ? (
        <div className="flex flex-col items-start gap-3">
          <p>
            <FormattedMessage id="markets.list.loadFailed" />
          </p>
          <button type="button" onClick={() => void list.refetch()} className={outline}>
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : list.data === undefined ? (
        <p role="status">
          <FormattedMessage id="common.loading" />
        </p>
      ) : items.length === 0 ? (
        <p className="text-text-muted">
          <FormattedMessage id="markets.list.empty" />
        </p>
      ) : (
        <>
          {open.length > 0 && <ul className="flex flex-col gap-2">{open.map(row)}</ul>}
          {picked.length > 0 && (
            <section aria-labelledby={pickedId} className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-2">
                <h2 id={pickedId} className="font-medium">
                  <FormattedMessage id="markets.list.picked" />
                </h2>
                {canRemove && !confirmingClear && (
                  <button
                    type="button"
                    onClick={() => setConfirmingClear(true)}
                    className={outline}
                  >
                    <FormattedMessage id="markets.list.clear" />
                  </button>
                )}
              </div>
              {canRemove && confirmingClear && (
                <div
                  role="group"
                  className="flex flex-col gap-2 rounded-card border border-border p-3"
                >
                  <p>
                    <FormattedMessage id="markets.list.clear.ask" />
                  </p>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      disabled={clear.isPending}
                      onClick={() => {
                        setNotice(null)
                        clear.mutate(undefined, { onError: () => setNotice('clearFailed') })
                        setConfirmingClear(false)
                      }}
                      className={`${outline} bg-primary text-on-primary`}
                    >
                      <FormattedMessage id="markets.list.clear.confirm" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirmingClear(false)}
                      className={outline}
                    >
                      <FormattedMessage id="markets.list.clear.cancel" />
                    </button>
                  </div>
                </div>
              )}
              <ul className="flex flex-col gap-2">{picked.map(row)}</ul>
            </section>
          )}
        </>
      )}
    </main>
  )
}
