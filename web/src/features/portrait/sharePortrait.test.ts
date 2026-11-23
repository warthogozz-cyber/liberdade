import { describe, expect, it, vi } from 'vitest'
import { sharePortrait } from './sharePortrait'

function canvas() {
  return {
    toBlob: (callback: (blob: Blob | null) => void) =>
      callback(new Blob(['x'], { type: 'image/png' })),
  } as unknown as HTMLCanvasElement
}

describe('sharePortrait', () => {
  it('sharesWhenCanShare', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    const download = vi.fn()
    const nav = { canShare: () => true, share } as unknown as Navigator
    expect(await sharePortrait(canvas(), '2026-11', { nav, download })).toBe('shared')
    const { files } = share.mock.calls[0][0] as { files: File[] }
    expect(files[0]).toBeInstanceOf(File)
    expect(files[0].name).toBe('retrato-2026-11.png')
    expect(files[0].type).toBe('image/png')
    expect(download).not.toHaveBeenCalled()
  })

  it('downloadsOtherwise', async () => {
    const download = vi.fn()
    const nav = {} as Navigator
    expect(await sharePortrait(canvas(), '2026-11', { nav, download })).toBe('downloaded')
    expect(download).toHaveBeenCalledWith(expect.any(Blob), 'retrato-2026-11.png')
  })

  it('abortIsCancelled', async () => {
    const share = vi.fn().mockRejectedValue(new DOMException('no', 'AbortError'))
    const nav = { canShare: () => true, share } as unknown as Navigator
    expect(await sharePortrait(canvas(), '2026-11', { nav, download: vi.fn() })).toBe('cancelled')
  })
})

describe('sharePortrait fallbacks', () => {
  it('fallsBackToDownloadWhenShareFails', async () => {
    const share = vi.fn().mockRejectedValue(new DOMException('x', 'NotAllowedError'))
    const download = vi.fn()
    const nav = { canShare: () => true, share } as unknown as Navigator
    expect(await sharePortrait(canvas(), '2026-11', { nav, download })).toBe('downloaded')
    expect(download).toHaveBeenCalledTimes(1)
  })

  it('throwsWhenThereIsNoImage', async () => {
    const empty = {
      toBlob: (callback: (blob: Blob | null) => void) => callback(null),
    } as unknown as HTMLCanvasElement
    await expect(
      sharePortrait(empty, '2026-11', { nav: {} as Navigator, download: vi.fn() }),
    ).rejects.toThrow('no image')
  })
})
