import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { download } from './download'

beforeEach(() => {
  vi.useFakeTimers()
  URL.createObjectURL = vi.fn(() => 'blob:test')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('download', () => {
  it('clicksAnAnchorWithTheNameAndRevokesTheUrl', () => {
    const seen: { name: string; href: string; inDom: boolean }[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      seen.push({ name: this.download, href: this.href, inDom: this.isConnected })
    })
    const blob = new Blob(['x'])

    download(blob, 'retrato-2026-11.png')

    expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
    expect(seen).toEqual([{ name: 'retrato-2026-11.png', href: 'blob:test', inDom: true }])
    expect(document.querySelector('a[download]')).toBeNull()
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    vi.runAllTimers()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test')
  })
})
