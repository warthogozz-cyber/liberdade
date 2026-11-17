import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { appMessages, locale } from '@/shared/i18n'
import { Sheet } from './Sheet'

// Unmounting closes the sheet before the next test.
afterEach(cleanup)

function renderSheet() {
  const onOpenChange = vi.fn<(open: boolean) => void>()
  render(
    <IntlProvider locale={locale} messages={appMessages()}>
      <Sheet open onOpenChange={onOpenChange} title="Data e conta">
        <p>Quem gastou: Eu</p>
      </Sheet>
    </IntlProvider>,
  )
  return onOpenChange
}

describe('Sheet', () => {
  it('is a dialog named by its title', () => {
    renderSheet()

    expect(screen.getByRole('dialog', { name: 'Data e conta' })).toHaveTextContent(
      'Quem gastou: Eu',
    )
  })

  it('asks to close on Escape', () => {
    const onOpenChange = renderSheet()

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('asks to close with Fechar', () => {
    const onOpenChange = renderSheet()

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }))

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })
})
