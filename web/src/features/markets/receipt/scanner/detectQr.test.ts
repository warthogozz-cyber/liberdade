import { describe, expect, it } from 'vitest'
import { pickDetector } from './detectQr'

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
