import { postExport } from '@/shared/api/privacy'

/** The file name the API sent, without folders or control characters; `undefined` when there is none. */
function fileNameFrom(disposition: string | null): string | undefined {
  if (!disposition) return undefined
  let raw: string | undefined
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)?.[1]
  if (encoded) {
    try {
      raw = decodeURIComponent(encoded.trim())
    } catch {
      raw = undefined
    }
  }
  raw ??= /filename\s*=\s*"([^"]*)"/i.exec(disposition)?.[1]
  raw ??= /filename\s*=\s*([^;]+)/i.exec(disposition)?.[1]?.trim()
  if (!raw) return undefined
  // eslint-disable-next-line no-control-regex
  const cleaned = (raw.split(/[\\/]/).pop() ?? '').replace(/[\u0000-\u001f\u007f]/g, '').trim()
  return cleaned === '' || cleaned === '.' || cleaned === '..' ? undefined : cleaned
}

/**
 * Asks an export route for its zip (a POST with the CSRF token) and hands it to the browser as a
 * download, named as the API said or `fallbackName`. Failures throw the ApiError.
 */
export async function downloadZip(path: string, fallbackName: string): Promise<void> {
  const { blob, disposition } = await postExport(path)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFrom(disposition) ?? fallbackName
  link.hidden = true
  document.body.append(link)
  link.click()
  link.remove()
  // After the click, so the browser has taken the file before the address goes.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
