import { cleanup, fireEvent, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { KindTabs, type TransactionKind } from '@/features/transactions/KindTabs'
import { renderWithProviders } from '@/shared/testing'

afterEach(cleanup)

function Tabs({ initial = 'EXPENSE' }: { initial?: TransactionKind }) {
  const [kind, setKind] = useState<TransactionKind>(initial)
  return (
    <>
      <KindTabs value={kind} onChange={setKind} panelId="panel" />
      <div id="panel" role="tabpanel" />
    </>
  )
}

const tab = (name: string) => screen.getByRole('tab', { name })

describe('KindTabs', () => {
  it('shows the three kinds, with only the chosen one selected and reachable by Tab', () => {
    renderWithProviders(<Tabs />)

    expect(screen.getByRole('tablist')).toBeInTheDocument()
    expect(screen.getAllByRole('tab').map((each) => each.textContent)).toEqual([
      'Gasto',
      'Receita',
      'Transferência',
    ])
    expect(tab('Gasto')).toHaveAttribute('aria-selected', 'true')
    expect(tab('Gasto')).toHaveAttribute('tabindex', '0')
    expect(tab('Receita')).toHaveAttribute('aria-selected', 'false')
    expect(tab('Receita')).toHaveAttribute('tabindex', '-1')
    for (const each of screen.getAllByRole('tab')) {
      expect(each).toHaveAttribute('aria-controls', 'panel')
      expect(each).toHaveClass('min-h-12')
    }
  })

  it('arrowKeysMoveSelection', () => {
    renderWithProviders(<Tabs />)
    tab('Gasto').focus()

    fireEvent.keyDown(tab('Gasto'), { key: 'ArrowRight' })

    expect(tab('Receita')).toHaveAttribute('aria-selected', 'true')
    expect(tab('Receita')).toHaveFocus()
    expect(tab('Gasto')).toHaveAttribute('aria-selected', 'false')
  })

  it('wraps around with the arrows and jumps with Home and End', () => {
    renderWithProviders(<Tabs />)
    tab('Gasto').focus()

    fireEvent.keyDown(tab('Gasto'), { key: 'ArrowLeft' })
    expect(tab('Transferência')).toHaveAttribute('aria-selected', 'true')
    expect(tab('Transferência')).toHaveFocus()

    fireEvent.keyDown(tab('Transferência'), { key: 'ArrowRight' })
    expect(tab('Gasto')).toHaveAttribute('aria-selected', 'true')

    fireEvent.keyDown(tab('Gasto'), { key: 'End' })
    expect(tab('Transferência')).toHaveFocus()

    fireEvent.keyDown(tab('Transferência'), { key: 'Home' })
    expect(tab('Gasto')).toHaveFocus()
    expect(tab('Gasto')).toHaveAttribute('aria-selected', 'true')
  })

  it('selects a tab by click and ignores other keys', () => {
    renderWithProviders(<Tabs initial="INCOME" />)

    fireEvent.keyDown(tab('Receita'), { key: 'a' })
    expect(tab('Receita')).toHaveAttribute('aria-selected', 'true')

    fireEvent.click(tab('Transferência'))
    expect(tab('Transferência')).toHaveAttribute('aria-selected', 'true')
  })
})
