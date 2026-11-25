import { centsToApi } from '@/shared/money/money'
import type { KindedLine } from './kinds'
import { normalizeDescription } from './normalize'

/** Descriptions are stored cut at this length, so the key is computed from the same text. */
const MAX_DESCRIPTION_LENGTH = 140

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * The `import_key` of each line, in the lines' order: SHA-256 of `account|date|signed|normalized|n`, where `n`
 * counts the earlier lines of the file with the same date, signed amount and description. The signed amount
 * follows the final kind, so an OFX and a CSV of the same card agree whatever sign each file uses.
 */
export async function importKeys(accountId: string, lines: KindedLine[]): Promise<string[]> {
  const seen = new Map<string, number>()
  const texts = lines.map((l) => {
    const signed = centsToApi(l.kind === 'EXPENSE' ? -l.absCents : l.absCents)
    const normalized = normalizeDescription(l.description.trim().slice(0, MAX_DESCRIPTION_LENGTH))
    const base = `${accountId}|${l.occurredOn}|${signed}|${normalized}`
    const n = seen.get(base) ?? 0
    seen.set(base, n + 1)
    return `${base}|${n}`
  })
  return Promise.all(texts.map(sha256Hex))
}
