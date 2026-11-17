import { describe, expect, it } from 'vitest'
import {
  buildSecurityHeaders,
  resolveApiUrl,
  securityHeadersPlugin,
  toHeadersFile,
} from './securityHeaders.ts'

const API = 'https://api.liberdadefamilia.com.br'

function directive(csp: string, name: string): string {
  const found = csp
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name} `))
  if (!found) throw new Error(`no ${name} directive`)
  return found
}

describe('buildSecurityHeaders', () => {
  it('cspAllowsOnlyTheApiOrigin', () => {
    const csp = buildSecurityHeaders(API)['Content-Security-Policy']
    expect(directive(csp, 'connect-src')).toBe(`connect-src 'self' ${API}`)
  })

  it('cspUsesOriginNotPath', () => {
    const csp = buildSecurityHeaders('https://api.example.com/v1/')['Content-Security-Policy']
    expect(directive(csp, 'connect-src')).toBe("connect-src 'self' https://api.example.com")
  })

  it('cspHasNoUnsafeDirectives', () => {
    const csp = buildSecurityHeaders(API)['Content-Security-Policy']
    expect(directive(csp, 'script-src')).toBe("script-src 'self'")
    expect(csp).not.toContain('unsafe-eval')
    expect(csp).not.toContain('*')
    expect(directive(csp, 'style-src')).toBe("style-src 'self' 'unsafe-inline'")
  })

  it('cspLocksFramingAndBase', () => {
    const csp = buildSecurityHeaders(API)['Content-Security-Policy']
    expect(csp).toContain("frame-ancestors 'none'")
    expect(csp).toContain("base-uri 'self'")
    expect(csp).toContain("object-src 'none'")
  })

  it('hstsAndFriends', () => {
    const headers = buildSecurityHeaders(API)
    expect(headers['Strict-Transport-Security']).toBe('max-age=31536000; includeSubDomains')
    expect(headers['X-Content-Type-Options']).toBe('nosniff')
    expect(headers['Referrer-Policy']).toBe('no-referrer')
    expect(headers['Permissions-Policy']).toContain('publickey-credentials-get=(self)')
    expect(headers['Permissions-Policy']).toContain('publickey-credentials-create=(self)')
  })

  it('rejectsInvalidApiUrl', () => {
    expect(() => buildSecurityHeaders('')).toThrow()
    expect(() => buildSecurityHeaders('ftp://x')).toThrow()
    expect(() => buildSecurityHeaders('not a url')).toThrow()
  })
})

describe('toHeadersFile', () => {
  it('headersFileFormat', () => {
    const file = toHeadersFile({ 'X-A': '1', 'X-B': 'two words' })
    expect(file.split('\n')).toEqual(['/*', '  X-A: 1', '  X-B: two words', ''])
  })
})

describe('resolveApiUrl', () => {
  it('defaultsOnlyWhenAbsent', () => {
    expect(resolveApiUrl(undefined)).toBe('http://localhost:8080')
    expect(resolveApiUrl('https://api.example.com')).toBe('https://api.example.com')
    expect(() => buildSecurityHeaders(resolveApiUrl(''))).toThrow()
  })
})

describe('securityHeadersPlugin', () => {
  function configure(command: 'build' | 'serve', isPreview: boolean) {
    const plugin = securityHeadersPlugin()
    const hook = plugin.config as (
      config: { root: string },
      env: { command: string; mode: string; isPreview?: boolean },
    ) => { preview?: { headers: Record<string, string> } } | undefined
    return hook({ root: 'C:/does-not-exist' }, { command, mode: 'test', isPreview })
  }

  it('givesPreviewAndBuildTheHeadersButNotDev', () => {
    expect(configure('serve', false)).toBeUndefined()
    expect(configure('serve', true)?.preview?.headers['Content-Security-Policy']).toContain(
      'connect-src',
    )
    expect(configure('build', false)?.preview?.headers['X-Content-Type-Options']).toBe('nosniff')
  })
})
