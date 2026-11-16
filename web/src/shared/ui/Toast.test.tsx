import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { locale, messages } from '@/shared/i18n'
import { ToastProvider, useToast } from '@/shared/ui'

// Unmounting stops the toast's timer before the next test.
afterEach(cleanup)

type Show = ReturnType<typeof useToast>['show']

/** Renders the provider and hands back its `show`. */
function renderProvider(): Show {
  let show: Show | undefined
  function Probe() {
    show = useToast().show
    return null
  }
  render(
    <IntlProvider locale={locale} messages={messages}>
      <ToastProvider>
        <Probe />
      </ToastProvider>
    </IntlProvider>,
  )
  // The update lands inside act, as a click would.
  return (toast) => act(() => show!(toast))
}

const region = () => screen.getByRole('region', { name: 'Notificações' })

describe('ToastProvider', () => {
  it('shows the message in the Notificações region', async () => {
    const show = renderProvider()

    show({ message: 'Gasto salvo' })

    expect(await within(region()).findByText('Gasto salvo')).toBeInTheDocument()
  })

  it('calls the action and closes the toast when the action is clicked', async () => {
    const show = renderProvider()
    const onClick = vi.fn<() => void>()

    show({
      message: 'Gasto salvo',
      action: { label: 'Desfazer', altText: 'Desfazer o último gasto', onClick },
    })
    fireEvent.click(await within(region()).findByRole('button', { name: 'Desfazer' }))

    expect(onClick).toHaveBeenCalledTimes(1)
    expect(within(region()).queryByText('Gasto salvo')).not.toBeInTheDocument()
  })

  it('replaces the current toast with a new one', async () => {
    const show = renderProvider()

    show({ message: 'Gasto salvo' })
    await within(region()).findByText('Gasto salvo')
    show({ message: 'Gasto desfeito' })

    expect(await within(region()).findByText('Gasto desfeito')).toBeInTheDocument()
    expect(within(region()).queryByText('Gasto salvo')).not.toBeInTheDocument()
    expect(within(region()).getAllByRole('listitem')).toHaveLength(1)
  })
})
