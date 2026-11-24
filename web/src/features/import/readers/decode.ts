const HEADER_BYTES = 2048

function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text
}

function hasUtf8Bom(bytes: Uint8Array): boolean {
  return bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf
}

/** The OFX header names the encoding: `encoding="..."` in XML, CHARSET/ENCODING in SGML. */
function ofxEncoding(bytes: Uint8Array): 'utf-8' | 'windows-1252' {
  const head = new TextDecoder('windows-1252').decode(bytes.subarray(0, HEADER_BYTES))
  if (/^\s*<\?xml/i.test(head)) {
    const declared = /<\?xml[^>]*encoding\s*=\s*["']([^"']+)["']/i.exec(head)?.[1]
    return declared && !/^utf-?8$/i.test(declared) ? 'windows-1252' : 'utf-8'
  }
  const charset = /^CHARSET:\s*(\S+)/im.exec(head)?.[1]
  const encoding = /^ENCODING:\s*(\S+)/im.exec(head)?.[1]
  if (charset && /^utf-?8$/i.test(charset)) return 'utf-8'
  if (!charset && encoding && /^utf-?8$/i.test(encoding)) return 'utf-8'
  return 'windows-1252'
}

/**
 * Turns the file's bytes into text. OFX follows its own header (windows-1252 by default in SGML);
 * CSV tries strict UTF-8 and falls back to windows-1252. A BOM is dropped.
 */
export function decode(bytes: Uint8Array, hint: 'ofx' | 'csv'): string {
  if (hasUtf8Bom(bytes)) return stripBom(new TextDecoder('utf-8').decode(bytes))
  if (hint === 'ofx') {
    return stripBom(new TextDecoder(ofxEncoding(bytes)).decode(bytes))
  }
  try {
    return stripBom(new TextDecoder('utf-8', { fatal: true }).decode(bytes))
  } catch {
    return stripBom(new TextDecoder('windows-1252').decode(bytes))
  }
}
