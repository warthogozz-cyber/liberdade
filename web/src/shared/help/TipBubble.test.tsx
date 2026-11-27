import { cleanup } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { isHelpOpen } from './helpStore'
import TipBubble from './TipBubble'

afterEach(cleanup)

describe('TipBubble', () => {
  it('marksHelpOpenWhileMounted', () => {
    const view = renderWithProviders(<TipBubble message="Oi" onDismiss={() => {}} />)
    expect(isHelpOpen()).toBe(true)

    view.unmount()
    expect(isHelpOpen()).toBe(false)
  })
})
