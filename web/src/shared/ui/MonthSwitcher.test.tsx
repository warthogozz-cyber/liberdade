import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MIN_MONTH, locale, messages } from '@/shared/i18n'
import { MonthSwitcher } from './MonthSwitcher'

afterEach(cleanup)

function renderSwitcher(month: string, max = '2027-11', onChange = vi.fn()) {
  render(
    <IntlProvider locale={locale} messages={messages}>
      <MonthSwitcher month={month} onChange={onChange} max={max} />
    </IntlProvider>,
  )
  return onChange
}

describe('MonthSwitcher', () => {
  it('namesTheMonth', () => {
    renderSwitcher('2026-11')

    expect(screen.getByText('Novembro de 2026')).toBeInTheDocument()
  })

  it('stopsAtBounds', () => {
    renderSwitcher(MIN_MONTH)
    expect(screen.getByRole('button', { name: 'Mês anterior' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Próximo mês' })).toBeEnabled()
    cleanup()

    renderSwitcher('2027-11', '2027-11')
    expect(screen.getByRole('button', { name: 'Próximo mês' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Mês anterior' })).toBeEnabled()
  })

  it('steps one month, across the year', () => {
    const onChange = renderSwitcher('2026-01')

    fireEvent.click(screen.getByRole('button', { name: 'Mês anterior' }))
    fireEvent.click(screen.getByRole('button', { name: 'Próximo mês' }))

    expect(onChange.mock.calls).toEqual([['2025-12'], ['2026-02']])
  })
})
