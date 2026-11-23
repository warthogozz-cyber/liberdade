/** Hands a file the app already holds to the browser as a download. Nothing leaves the device. */
export function download(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.hidden = true
  document.body.append(link)
  link.click()
  link.remove()
  // After the click, so the browser has taken the file before the address goes.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
