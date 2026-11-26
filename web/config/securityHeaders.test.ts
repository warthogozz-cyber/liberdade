import { describe, expect, it } from 'vitest'
import {
  buildEmbedHeaders,
  buildSecurityHeaders,
  embedMiddleware,
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

describe('frame policy', () => {
  it('appCspAllowsOnlyGoogleFrames', () => {
    const csp = buildSecurityHeaders(API)['Content-Security-Policy']
    expect(directive(csp, 'frame-src')).toBe("frame-src 'self' https://www.google.com")
    expect(directive(csp, 'script-src')).toBe("script-src 'self'")
  })

  it('geolocationAndCameraSelfRestOff', () => {
    const policy = buildSecurityHeaders(API)['Permissions-Policy']
    expect(policy).toBe(
      'camera=(self), microphone=(), geolocation=(self), payment=(), usb=(), publickey-credentials-get=(self), publickey-credentials-create=(self)',
    )
  })

  it('embedBlockReplacesCsp', () => {
    const file = toHeadersFile(buildSecurityHeaders(API))
    const block = file.slice(file.indexOf('/embed/instagram*'))
    expect(block).toContain('  ! Content-Security-Policy')
    expect(block).toContain('  ! X-Frame-Options')
    expect(block).toContain('  ! Permissions-Policy')
    expect(block).toContain(
      `  Content-Security-Policy: ${buildEmbedHeaders()['Content-Security-Policy']}`,
    )
    expect(block).toContain('  X-Frame-Options: SAMEORIGIN')
    expect(buildEmbedHeaders()['Content-Security-Policy']).toBe(
      "default-src 'none'; script-src 'self' https://www.instagram.com; frame-src https://www.instagram.com; img-src https://*.cdninstagram.com data:; style-src 'unsafe-inline'; frame-ancestors 'self'; base-uri 'none'",
    )
    expect(block).toContain('/embed/instagram.js')
    expect(block).toContain('Access-Control-Allow-Origin: *')
    expect(file.startsWith('/*\n')).toBe(true)
  })
})

describe('embedMiddleware', () => {
  function run(url: string) {
    const headers: Record<string, string> = {}
    const req = { url }
    const res = { setHeader: (name: string, value: string) => (headers[name] = value) }
    let nexted = false
    embedMiddleware()(req, res, () => {
      nexted = true
      // The preview server's own header middleware runs after ours.
      res.setHeader('Content-Security-Policy', 'app-csp')
    })
    return { headers, url: req.url, nexted }
  }

  it('rewritesTheExtensionlessPageAndAppliesTheEmbedCsp', () => {
    const out = run('/embed/instagram')
    expect(out.url).toBe('/embed/instagram.html')
    expect(out.headers['Content-Security-Policy']).toBe(
      buildEmbedHeaders()['Content-Security-Policy'],
    )
    expect(out.headers['X-Frame-Options']).toBe('SAMEORIGIN')
    expect(out.nexted).toBe(true)
  })

  it('allowsCorsOnTheScriptOnly', () => {
    expect(run('/embed/instagram.js').headers['Access-Control-Allow-Origin']).toBe('*')
    expect(run('/embed/instagram').headers['Access-Control-Allow-Origin']).toBeUndefined()
  })

  it('leavesOtherPathsAlone', () => {
    const out = run('/markets')
    expect(out.headers).toEqual({ 'Content-Security-Policy': 'app-csp' })
    expect(out.url).toBe('/markets')
    expect(out.nexted).toBe(true)
  })
})

describe('toHeadersFile', () => {
  it('headersFileFormat', () => {
    const file = toHeadersFile({ 'X-A': '1', 'X-B': 'two words' })
    expect(file.split('\n').slice(0, 4)).toEqual(['/*', '  X-A: 1', '  X-B: two words', ''])
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
