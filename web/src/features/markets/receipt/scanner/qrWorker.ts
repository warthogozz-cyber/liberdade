import jsQR from 'jsqr'

/** Decodes one frame (`ImageData`) at a time and answers with the QR text, or null. */
self.onmessage = (event: MessageEvent<ImageData>) => {
  const { data, width, height } = event.data
  const result = jsQR(data, width, height, { inversionAttempts: 'dontInvert' })
  self.postMessage(result === null ? null : result.data)
}
