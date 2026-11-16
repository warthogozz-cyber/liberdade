import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { DatasetComponent, GridComponent } from 'echarts/components'
import { init, use as register, type EChartsCoreOption, type ECharts } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { useEffect, useRef } from 'react'

// Only what the dashboard draws goes in the bundle (D19).
register([PieChart, LineChart, BarChart, GridComponent, DatasetComponent, SVGRenderer])

interface EChartProps {
  option: EChartsCoreOption
  height: number
  /** Called with the `id` of the clicked slice or bar; items without an id are ignored. */
  onItemClick?: (id: string) => void
}

/**
 * One ECharts chart, drawn as SVG. It is for the eyes only: a screen reader gets the table next to
 * it, so the container is hidden from it.
 */
export function EChart({ option, height, onItemClick }: EChartProps) {
  const element = useRef<HTMLDivElement>(null)
  const chart = useRef<ECharts | null>(null)
  const latestClick = useRef(onItemClick)

  useEffect(() => {
    latestClick.current = onItemClick
  })

  useEffect(() => {
    const target = element.current
    if (!target) return
    const instance = init(target, undefined, { renderer: 'svg' })
    chart.current = instance
    instance.on('click', (params: unknown) => {
      const id = (params as { data?: { id?: unknown } }).data?.id
      if (typeof id === 'string') latestClick.current?.(id)
    })
    const observer =
      typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => instance.resize())
    observer?.observe(target)
    return () => {
      observer?.disconnect()
      instance.dispose()
      chart.current = null
    }
  }, [])

  useEffect(() => {
    chart.current?.setOption(option, true)
  }, [option])

  return <div ref={element} aria-hidden="true" data-chart="" style={{ height }} />
}
