import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CategoryPicker } from '@/features/transactions/CategoryPicker'
import type { CategoryView } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'

afterEach(cleanup)

function category(name: string, archivedAt: string | null = null): CategoryView {
  return {
    id: crypto.randomUUID(),
    name,
    kind: 'EXPENSE',
    icon: null,
    color: null,
    parentId: null,
    archivedAt,
    isFixed: false,
  }
}

describe('CategoryPicker', () => {
  it('chipShowsColorDot', () => {
    const blue = { ...category('Azulada'), icon: '🛒', color: 'BLUE' as const }
    renderWithProviders(
      <CategoryPicker
        categories={[blue, category('Sem')]}
        value={undefined}
        onChange={vi.fn()}
        onSheetChange={vi.fn()}
      />,
    )

    const chip = screen.getByRole('radio', { name: /Azulada/ }).closest('label')!
    const dot = chip.querySelector('[aria-hidden="true"]')!
    expect(dot.className).toContain('bg-blue-500')
    // The dot comes before the icon.
    expect(dot.compareDocumentPosition(chip.lastChild!)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    const plain = screen.getByRole('radio', { name: 'Sem' }).closest('label')!
    expect(plain.querySelector('[aria-hidden="true"]')).toBeNull()
  })

  it('leaves archived categories out of the chips and out of Mais', () => {
    // Archived first, so it would take a chip if the filter were gone.
    const categories = [
      category('Antiga', '2026-11-01T12:00:00Z'),
      ...['A', 'B', 'C', 'D', 'E', 'F', 'G'].map((name) => category(name)),
    ]
    renderWithProviders(
      <CategoryPicker
        categories={categories}
        value={undefined}
        onChange={vi.fn()}
        onSheetChange={vi.fn()}
      />,
    )

    expect(screen.getAllByRole('radio')).toHaveLength(6)
    expect(screen.queryByRole('radio', { name: 'Antiga' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Mais' }))
    const sheet = screen.getByRole('dialog')

    expect(within(sheet).getByRole('button', { name: 'G' })).toBeTruthy()
    expect(within(sheet).queryByRole('button', { name: 'Antiga' })).toBeNull()
  })
})
