import { cleanup, render } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const chart = vi.hoisted(() => ({
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
  on: vi.fn(),
}))
const init = vi.hoisted(() => vi.fn())

vi.mock('echarts/core', () => ({ init, use: vi.fn() }))
vi.mock('echarts/charts', () => ({ BarChart: {}, LineChart: {}, PieChart: {} }))
vi.mock('echarts/components', () => ({ DatasetComponent: {}, GridComponent: {} }))
vi.mock('echarts/renderers', () => ({ SVGRenderer: {} }))

import { EChart } from './EChart'

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('EChart', () => {
  it('disposesOnUnmount', () => {
    init.mockReturnValue(chart)
    const { container, unmount } = render(<EChart option={{ animation: false }} height={200} />)

    expect(init).toHaveBeenCalledTimes(1)
    expect(chart.setOption).toHaveBeenCalledWith({ animation: false }, true)
    expect(container.firstElementChild).toHaveAttribute('aria-hidden', 'true')
    expect(chart.dispose).not.toHaveBeenCalled()

    unmount()

    expect(chart.dispose).toHaveBeenCalledTimes(1)
  })

  it('forwardsItemClick', () => {
    init.mockReturnValue(chart)
    const onItemClick = vi.fn()
    render(<EChart option={{}} height={200} onItemClick={onItemClick} />)

    const [event, handler] = chart.on.mock.calls[0] as [string, (params: unknown) => void]
    expect(event).toBe('click')
    handler({ data: { id: 'abc' } })
    handler({ data: { id: null } })

    expect(onItemClick).toHaveBeenCalledTimes(1)
    expect(onItemClick).toHaveBeenCalledWith('abc')
  })
})
