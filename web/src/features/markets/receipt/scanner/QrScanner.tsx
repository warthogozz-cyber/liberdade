import { useEffect, useRef, useState } from 'react'
import { useIntl } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from '../../messages.pt-BR'
import { createReader, pickDetector } from './detectQr'
import type { QrReader, QrWindow } from './detectQr'

registerMessages(marketsMessages)

/** How often a frame goes to the reader; the next one waits for the previous to finish. */
const FRAME_INTERVAL_MS = 200
/** Consecutive reader errors after which the camera is given up for the alternatives. */
const MAX_FAILURES = 5

interface QrScannerProps {
  onResult: (text: string) => void
  /** The camera cannot be used (denied, none, or no reader): the caller shows paste and type. */
  onUnavailable: () => void
}

/**
 * The receipt QR reader (D16). It asks for the back camera only once mounted (the person tapped to
 * read), and stops every track on a result, on unmount and when the tab goes to the background.
 * The video never leaves the device.
 */
export default function QrScanner({ onResult, onUnavailable }: QrScannerProps) {
  const intl = useIntl()
  const videoRef = useRef<HTMLVideoElement>(null)
  const [state, setState] = useState<'scanning' | 'unavailable' | 'paused'>('scanning')
  // The callbacks may change between renders; the camera must not restart because of that.
  const callbacks = useRef({ onResult, onUnavailable })
  useEffect(() => {
    callbacks.current = { onResult, onUnavailable }
  })

  useEffect(() => {
    let stopped = false
    let stream: MediaStream | undefined
    let reader: QrReader | undefined
    let timer: ReturnType<typeof setTimeout> | undefined
    let failures = 0

    function stop() {
      stopped = true
      clearTimeout(timer)
      stream?.getTracks().forEach((track) => track.stop())
      reader?.dispose()
      if (videoRef.current !== null) videoRef.current.srcObject = null
    }
    function fail() {
      stop()
      setState('unavailable')
      callbacks.current.onUnavailable()
    }
    function onVisibility() {
      if (document.visibilityState === 'hidden' && !stopped) {
        stop()
        setState('paused')
      }
    }
    document.addEventListener('visibilitychange', onVisibility)

    async function tick(video: HTMLVideoElement, activeReader: QrReader) {
      let text: string | null = null
      if (video.readyState >= 2) {
        try {
          text = await activeReader.scan(video)
          failures = 0
        } catch {
          // A frame that fails is skipped, but a reader that keeps failing is not worth the camera.
          failures += 1
          if (!stopped && failures >= MAX_FAILURES) return fail()
        }
      }
      if (stopped) return
      if (text !== null && text !== '') {
        stop()
        callbacks.current.onResult(text)
        return
      }
      timer = setTimeout(() => void tick(video, activeReader), FRAME_INTERVAL_MS)
    }

    async function start() {
      const kind = await pickDetector(window as QrWindow)
      if (stopped) return
      if (kind === 'none' || !navigator.mediaDevices?.getUserMedia) return fail()
      try {
        const opened = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        })
        if (stopped) {
          opened.getTracks().forEach((track) => track.stop())
          return
        }
        stream = opened
        reader = createReader(kind, window as QrWindow)
      } catch {
        // Denied, no camera, busy or over-constrained: all mean the same to the person.
        return fail()
      }
      const video = videoRef.current
      if (video === null) return fail()
      video.srcObject = stream
      await Promise.resolve(video.play()).catch(() => undefined)
      if (stopped) return
      void tick(video, reader)
    }
    void start()

    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      stop()
    }
  }, [])

  if (state !== 'scanning') {
    return (
      <p role="alert" className="text-sm">
        {intl.formatMessage({
          id:
            state === 'paused'
              ? 'markets.receipt.scanner.paused'
              : 'markets.receipt.scanner.unavailable',
        })}
      </p>
    )
  }
  return (
    <div className="flex flex-col gap-2">
      <video
        ref={videoRef}
        muted
        playsInline
        aria-label={intl.formatMessage({ id: 'markets.receipt.scanner.video' })}
        className="aspect-square w-full rounded-field bg-black object-cover"
      />
      <p role="status" className="text-sm">
        {intl.formatMessage({ id: 'markets.receipt.scanner.hint' })}
      </p>
    </div>
  )
}
