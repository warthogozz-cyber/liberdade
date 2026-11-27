/**
 * Build-time switch for the jsQR fallback: with it off, a browser without a native `BarcodeDetector`
 * has no scanner and the person pastes or types the key (D16).
 */
export const JSQR_ENABLED = true

export type DetectorKind = 'native' | 'worker' | 'none'

/** A reader of one video frame: the decoded text, or null when no QR is in view. */
export interface QrReader {
  scan(video: HTMLVideoElement): Promise<string | null>
  dispose(): void
}

/** The slice of the Barcode Detection API this feature uses (it is not in TypeScript's DOM lib). */
interface NativeDetector {
  detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]>
}
interface NativeDetectorClass {
  new (options: { formats: string[] }): NativeDetector
  getSupportedFormats?: () => Promise<string[]>
}
export interface QrWindow {
  BarcodeDetector?: NativeDetectorClass
}

/** The native detector when the browser has one that reads QR codes, else jsQR if approved, else none. */
export async function pickDetector(
  win: QrWindow,
  jsqrEnabled: boolean = JSQR_ENABLED,
): Promise<DetectorKind> {
  const native = win.BarcodeDetector
  if (native !== undefined) {
    try {
      const formats = (await native.getSupportedFormats?.()) ?? []
      if (formats.includes('qr_code')) return 'native'
    } catch {
      // A detector that cannot list its formats is treated as absent.
    }
  }
  return jsqrEnabled ? 'worker' : 'none'
}

/** Frames are shrunk to this width before decoding: a QR stays readable and the worker stays quick. */
const MAX_FRAME_WIDTH = 640

function nativeReader(native: NativeDetectorClass): QrReader {
  const detector = new native({ formats: ['qr_code'] })
  return {
    scan: async (video) => (await detector.detect(video))[0]?.rawValue ?? null,
    dispose: () => undefined,
  }
}

function workerReader(): QrReader {
  const worker = new Worker(new URL('./qrWorker.ts', import.meta.url), { type: 'module' })
  const canvas = document.createElement('canvas')
  let pending: { resolve: (text: string | null) => void; reject: (error: Error) => void } | null =
    null
  let failure: Error | null = null
  // Attached once, at construction: a worker that fails to load fires `error` before any scan.
  worker.onmessage = (event: MessageEvent<string | null>) => {
    const waiting = pending
    pending = null
    waiting?.resolve(event.data)
  }
  worker.onerror = (event) => {
    failure = new Error(event.message || 'The QR worker failed')
    const waiting = pending
    pending = null
    waiting?.reject(failure)
  }
  return {
    scan(video) {
      if (failure !== null) return Promise.reject(failure)
      // One frame at a time: a frame asked for while another is still decoding is dropped.
      if (pending !== null || video.videoWidth === 0) return Promise.resolve(null)
      const scale = Math.min(1, MAX_FRAME_WIDTH / video.videoWidth)
      canvas.width = Math.round(video.videoWidth * scale)
      canvas.height = Math.round(video.videoHeight * scale)
      const context = canvas.getContext('2d', { willReadFrequently: true })
      if (context === null) return Promise.resolve(null)
      context.drawImage(video, 0, 0, canvas.width, canvas.height)
      const frame = context.getImageData(0, 0, canvas.width, canvas.height)
      return new Promise<string | null>((resolve, reject) => {
        // Post first: a post that throws must not leave a frame waiting forever.
        worker.postMessage(frame, [frame.data.buffer])
        pending = { resolve, reject }
      })
    },
    dispose: () => worker.terminate(),
  }
}

/** The reader for a picked kind. The frame never leaves the device: the worker is a same-origin script. */
export function createReader(kind: Exclude<DetectorKind, 'none'>, win: QrWindow): QrReader {
  return kind === 'native' && win.BarcodeDetector !== undefined
    ? nativeReader(win.BarcodeDetector)
    : workerReader()
}
