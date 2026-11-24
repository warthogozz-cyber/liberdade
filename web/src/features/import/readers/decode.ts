function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text
}

/** The encoding a byte-order mark names, or null without one. */
function bomEncoding(bytes: Uint8Array): 'utf-8' | 'utf-16le' | 'utf-16be' | null {
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) return 'utf-8'
  if (bytes[0] === 0xff && bytes[1] === 0xfe) return 'utf-16le'
  if (bytes[0] === 0xfe && bytes[1] === 0xff) return 'utf-16be'
  return null
}

/**
 * Turns the file's bytes into text. A byte-order mark decides (UTF-8 or UTF-16) and is dropped. Without one the
 * bytes are tried as strict UTF-8 and read as windows-1252 only when that fails. This holds for OFX too, whatever
 * its header says: SGML headers often pair `ENCODING:UTF-8` with `CHARSET:NONE` or name a charset the body does not
 * use, while windows-1252 text with accents is practically never valid UTF-8, so the bytes are the better witness.
 */
export function decode(bytes: Uint8Array): string {
  const bom = bomEncoding(bytes)
  if (bom) return stripBom(new TextDecoder(bom).decode(bytes))
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    return new TextDecoder('windows-1252').decode(bytes)
  }
}
