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
      'camera=(), microphone=(), geolocation=(), payment=(), usb=(), publickey-credentials-get=(self), publickey-credentials-create=(self)',
  }
}

/** The Cloudflare Pages `_headers` format: a path line, then one indented line per header. */
export function toHeadersFile(headers: Record<string, string>): string {
  const lines = Object.entries(headers).map(([name, value]) => `  ${name}: ${value}`)
  return ['/*', ...lines, ''].join('\n')
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
