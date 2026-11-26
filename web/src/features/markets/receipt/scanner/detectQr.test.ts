import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createReader, pickDetector } from './detectQr'

const withFormats = (formats: string[]) =>
  ({ getSupportedFormats: () => Promise.resolve(formats) }) as never

describe('pickDetector', () => {
  it('picksNativeFirst', async () => {
    expect(await pickDetector({ BarcodeDetector: withFormats(['qr_code', 'ean_13']) })).toBe(
      'native',
    )
  })

  it('fallsBackToTheWorkerWithoutQrSupport', async () => {
    expect(await pickDetector({})).toBe('worker')
    expect(await pickDetector({ BarcodeDetector: withFormats(['ean_13']) })).toBe('worker')
  })

  it('treatsADetectorThatCannotListFormatsAsAbsent', async () => {
    const broken = { getSupportedFormats: () => Promise.reject(new Error('no')) } as never
    expect(await pickDetector({ BarcodeDetector: broken })).toBe('worker')
  })
})

describe('pickDetector without any reader', () => {
  it('picksNoneWhenJsqrIsOff', async () => {
    expect(await pickDetector({}, false)).toBe('none')
    expect(await pickDetector({ BarcodeDetector: withFormats(['ean_13']) }, false)).toBe('none')
  })
})

describe('worker reader', () => {
  class FakeWorker {
    static last: FakeWorker
    onmessage: ((event: { data: string | null }) => void) | null = null
    onerror: ((event: { message: string }) => void) | null = null
    posted: unknown[] = []
    constructor() {
      FakeWorker.last = this
    }
    postMessage(message: unknown) {
      this.posted.push(message)
    }
    terminate() {}
  }
  const video = { videoWidth: 100, videoHeight: 100 } as HTMLVideoElement

  beforeEach(() => {
    vi.stubGlobal('Worker', FakeWorker)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: () => undefined,
      getImageData: () => ({ data: { buffer: new ArrayBuffer(4) } }),
    } as never)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('workerLoadErrorRejectsScan', async () => {
    const reader = createReader('worker', {})
    // A module worker that fails to load fires `error` before the first scan.
    FakeWorker.last.onerror?.({ message: 'load failed' })

    await expect(reader.scan(video)).rejects.toThrow('load failed')
    await expect(reader.scan(video)).rejects.toThrow('load failed')
  })

  it('workerLoadErrorRejectsAPendingScan', async () => {
    const reader = createReader('worker', {})
    const pending = reader.scan(video)
    FakeWorker.last.onerror?.({ message: 'died' })

    await expect(pending).rejects.toThrow('died')
  })

  it('workerReaderDropsFrameWhilePending', async () => {
    const reader = createReader('worker', {})
    const first = reader.scan(video)

    expect(await reader.scan(video)).toBeNull()
    expect(FakeWorker.last.posted).toHaveLength(1)

    FakeWorker.last.onmessage?.({ data: null })
    await first
  })

  it('workerReaderResolvesResult', async () => {
    const reader = createReader('worker', {})
    const scan = reader.scan(video)
    FakeWorker.last.onmessage?.({ data: 'texto-do-qr' })

    expect(await scan).toBe('texto-do-qr')
    const next = reader.scan(video)
    FakeWorker.last.onmessage?.({ data: null })
    expect(await next).toBeNull()
    expect(FakeWorker.last.posted).toHaveLength(2)
  })
})
