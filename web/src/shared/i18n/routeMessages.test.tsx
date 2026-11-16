import { act, cleanup, render, screen } from '@testing-library/react'
import { IntlProvider, useIntl, type MessageDescriptor } from 'react-intl'
import { afterEach, describe, expect, it } from 'vitest'
import { appMessages, registerMessages, useAppMessages } from './routeMessages'

afterEach(cleanup)

function Probe({ id }: { id: string }) {
  const intl = useIntl()
  return <p>{intl.formatMessage({ id } as MessageDescriptor)}</p>
}

function Harness({ id }: { id: string }) {
  return (
    <IntlProvider locale="pt-BR" messages={useAppMessages()} onError={() => {}}>
      <Probe id={id} />
    </IntlProvider>
  )
}

describe('registerMessages', () => {
  it('keeps the base messages and adds the ids of a registered bundle', () => {
    registerMessages({ 'test.first': 'Primeiro' })
    render(<Harness id="test.first" />)

    expect(screen.getByText('Primeiro')).toBeInTheDocument()
    render(<Harness id="app.name" />)
    expect(screen.getByText('Liberdade')).toBeInTheDocument()
  })

  it('re-renders a provider that is already mounted', () => {
    render(<Harness id="test.late" />)
    expect(screen.getByText('test.late')).toBeInTheDocument()

    act(() => registerMessages({ 'test.late': 'Chegou depois' }))

    expect(screen.getByText('Chegou depois')).toBeInTheDocument()
  })

  it('does not change the messages for a bundle that is already merged', () => {
    registerMessages({ 'test.same': 'Igual' })
    const before = appMessages()
    registerMessages({ 'test.same': 'Igual' })

    expect(appMessages()).toBe(before)
  })
})
