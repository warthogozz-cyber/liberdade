import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import type { ShoppingListItem } from '@/shared/api/markets'
import { renderWithProviders } from '@/shared/testing'
import { ListItemRow } from './ListItemRow'

afterEach(cleanup)

const item: ShoppingListItem = {
  id: '00000000-0000-4000-8000-00000000c001',
  productId: '00000000-0000-4000-8000-00000000a001',
  name: 'Arroz tipo 1',
  unit: 'KG',
  quantity: '2',
  checked: false,
  position: 0,
  prices: [
    {
      marketRef: 'osm-n-9001',
      marketName: 'Mercado Exemplo 1',
      unitPrice: '4.9900',
      unit: 'KG',
      observedOn: '2026-11-12',
    },
  ],
}

describe('ListItemRow', () => {
  it('pricesPerMarket', () => {
    seedMockFamily()
    renderWithProviders(
      <ul>
        <ListItemRow
          item={item}
          canEdit
          canRemove
          isFirst
          isLast
          onCheck={vi.fn()}
          onQuantity={vi.fn()}
          onMove={vi.fn()}
          onRemove={vi.fn()}
        />
      </ul>,
    )

    expect(screen.getByText('Mercado Exemplo 1 · R$ 4,99 · 12/11/2026')).toBeVisible()
  })
})
