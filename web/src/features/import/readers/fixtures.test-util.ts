// The app tsconfig has no Node types, so the specifiers are variables that tsc does not resolve.
const FS = 'node:fs'
const PATH = 'node:path'
const URL_MODULE = 'node:url'

type Fs = { readFileSync(path: string): Uint8Array }
type Path = { dirname(p: string): string; join(...parts: string[]): string }
type Url = { fileURLToPath(u: string): string }

/** Reads a sample statement as raw bytes, exactly as the browser would hand them over. */
export async function fixtureBytes(name: string): Promise<Uint8Array> {
  const fs = (await import(/* @vite-ignore */ FS)) as Fs
  const path = (await import(/* @vite-ignore */ PATH)) as Path
  const url = (await import(/* @vite-ignore */ URL_MODULE)) as Url
  const dir = path.dirname(url.fileURLToPath(import.meta.url))
  return new Uint8Array(fs.readFileSync(path.join(dir, '..', 'fixtures', name)))
}
