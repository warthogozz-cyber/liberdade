import { download } from '@/shared/download'

export type ShareResult = 'shared' | 'downloaded' | 'cancelled'

function toBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('no image'))), 'image/png'),
  )
}

/**
 * Makes the PNG from the canvas and shares it as a file when the device can; otherwise it saves
 * `retrato-AAAA-MM.png`. Nothing is sent anywhere by the app.
 */
export async function sharePortrait(
  canvas: HTMLCanvasElement,
  month: string,
  deps: {
    nav: Navigator
    download: (blob: Blob, name: string) => void
  } = { nav: navigator, download },
): Promise<ShareResult> {
  const blob = await toBlob(canvas)
  const name = `retrato-${month}.png`
  const files = [new File([blob], name, { type: 'image/png' })]
  if (deps.nav.canShare?.({ files })) {
    try {
      await deps.nav.share({ files })
      return 'shared'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled'
      throw error
    }
  }
  deps.download(blob, name)
  return 'downloaded'
}
