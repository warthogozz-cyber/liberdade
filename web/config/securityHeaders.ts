import { rm } from 'node:fs/promises'
import { resolve } from 'node:path'
import { loadEnv } from 'vite'
import type { Plugin } from 'vite'

/** The same default as `src/shared/api/config.ts`, so a build without the variable matches the app. */
const DEFAULT_API_URL = 'http://localhost:8080'

/**
 * The API variable: absent means the local default; set but empty (or not an http(s) URL) throws, so
 * a pipeline whose variable came out blank fails the build instead of publishing a wrong CSP.
 */
export function resolveApiUrl(value: string | undefined): string {
  return value === undefined ? DEFAULT_API_URL : value
}

function apiOrigin(apiUrl: string): string {
  let url: URL
  try {
    url = new URL(apiUrl)
  } catch {
    throw new Error(`VITE_API_URL is not a valid URL: "${apiUrl}"`)
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error(`VITE_API_URL must be http or https, got "${url.protocol}"`)
  }
  return url.origin
}

/**
 * The CSP and companion headers (D4). `style-src` allows inline styles because Radix injects a
 * `<style>` tag for the dialog scroll lock and a nonce is impossible on static hosting; `script-src`
 * stays strict.
 */
export function buildSecurityHeaders(apiUrl: string): Record<string, string> {
  const csp = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    `connect-src 'self' ${apiOrigin(apiUrl)}`,
    "img-src 'self' data:",
    "font-src 'self'",
    "worker-src 'self'",
    "manifest-src 'self'",
    "frame-ancestors 'none'",
    // The Google Maps embed (D19/D20); the Instagram frame is the app's own `/embed/instagram` page.
    "frame-src 'self' https://www.google.com",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join('; ')
  return {
    'Content-Security-Policy': csp,
    'Strict-Transport-Security': 'max-age=31536000; includeSubDomains',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Permissions-Policy':
      'camera=(), microphone=(), geolocation=(self), payment=(), usb=(), publickey-credentials-get=(self), publickey-credentials-create=(self)',
  }
}

/** The isolated Instagram page and its script: one path prefix, served under their own headers. */
const EMBED_PATH = '/embed/instagram'
const EMBED_SCRIPT_PATH = '/embed/instagram.js'
const DETACHED_HEADERS = ['Content-Security-Policy', 'X-Frame-Options', 'Permissions-Policy']

/**
 * The headers of the isolated Instagram page (D20, spec 6.4): scripts only from itself and
 * instagram.com, and only the app may frame it.
 */
export function buildEmbedHeaders(): Record<string, string> {
  const csp = [
    "default-src 'none'",
    "script-src 'self' https://www.instagram.com",
    'frame-src https://www.instagram.com',
    'img-src https://*.cdninstagram.com data:',
    "style-src 'unsafe-inline'",
    "frame-ancestors 'self'",
    "base-uri 'none'",
  ].join('; ')
  return { 'Content-Security-Policy': csp, 'X-Frame-Options': 'SAMEORIGIN' }
}

/**
 * The Cloudflare Pages `_headers` format: a path line, then one indented line per header. The embed
 * block detaches (`!`) the app-wide CSP, frame and permission headers before setting its own, so the
 * two policies never stack. The script is fetched from the sandbox's opaque origin, which makes it a
 * CORS request, hence the wildcard on that one public static file.
 */
export function toHeadersFile(headers: Record<string, string>): string {
  const lines = Object.entries(headers).map(([name, value]) => `  ${name}: ${value}`)
  const embed = Object.entries(buildEmbedHeaders()).map(([name, value]) => `  ${name}: ${value}`)
  return [
    '/*',
    ...lines,
    '',
    `${EMBED_PATH}*`,
    ...DETACHED_HEADERS.map((name) => `  ! ${name}`),
    ...embed,
    '',
    EMBED_SCRIPT_PATH,
    '  Access-Control-Allow-Origin: *',
    '',
  ].join('\n')
}

interface MiddlewareRequest {
  url?: string
}
interface MiddlewareResponse {
  setHeader: (name: string, value: string) => unknown
}

/**
 * Preview and dev middleware that serves `/embed/instagram*` as Cloudflare would: the extensionless
 * path maps to its HTML file (Vite would answer with the app shell), under the embed headers.
 */
export function embedMiddleware() {
  return (req: MiddlewareRequest, res: MiddlewareResponse, next: () => void): void => {
    const path = (req.url ?? '').split('?')[0]
    if (path.startsWith(EMBED_PATH)) {
      for (const [name, value] of Object.entries(buildEmbedHeaders())) res.setHeader(name, value)
      if (path === EMBED_SCRIPT_PATH) res.setHeader('Access-Control-Allow-Origin', '*')
      // `preview.headers` is applied after this middleware and would put the app CSP back: the
      // equivalent of the `!` lines in `_headers` is to ignore those names from here on.
      const set = res.setHeader.bind(res)
      const detached = new Set(DETACHED_HEADERS.map((name) => name.toLowerCase()))
      res.setHeader = (name, value) => (detached.has(name.toLowerCase()) ? res : set(name, value))
      if (path === EMBED_PATH) req.url = `${EMBED_PATH}.html${(req.url ?? '').slice(path.length)}`
    }
    next()
  }
}

/**
 * Emits `dist/_headers` on build, serves the same headers on `vite preview` (so the E2E runs under the
 * real CSP), and drops MSW's worker from `dist` unless the build is the mocks build. The dev server
 * gets no CSP: Vite's HMR needs inline scripts and a websocket.
 */
export function securityHeadersPlugin(): Plugin {
  let headers: Record<string, string> = {}
  let keepMockWorker = false
  let outDir = ''

  return {
    name: 'security-headers',
    config(userConfig, { command, mode, isPreview }) {
      const root = resolve(userConfig.root ?? process.cwd())
      const env = loadEnv(
        mode,
        userConfig.envDir === false ? root : (userConfig.envDir ?? root),
        'VITE_',
      )
      keepMockWorker = env.VITE_API_MOCKS === 'true'
      if (command !== 'build' && !isPreview) return undefined
      headers = buildSecurityHeaders(resolveApiUrl(env.VITE_API_URL))
      return { preview: { headers } }
    },
    configureServer(server) {
      server.middlewares.use(embedMiddleware())
    },
    configurePreviewServer(server) {
      server.middlewares.use(embedMiddleware())
    },
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: '_headers', source: toHeadersFile(headers) })
    },
    async closeBundle() {
      if (!keepMockWorker) await rm(resolve(outDir, 'mockServiceWorker.js'), { force: true })
    },
  }
}
