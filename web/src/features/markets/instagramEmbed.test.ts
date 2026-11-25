import { afterEach, beforeEach, describe, expect, it } from 'vitest'
// @ts-expect-error The isolated page is plain browser JS served from public/, with no types.
import { acceptEmbed, listen } from '../../../public/embed/instagram.js'

const EMBED_JS = 'https://www.instagram.com/embed.js'

function quote(permalink: string, extra = ''): string {
  return `<blockquote class="instagram-media" data-instgrm-permalink="${permalink}" data-instgrm-version="14" ${extra}>Post text</blockquote>`
}

function send(source: unknown, data: unknown) {
  window.dispatchEvent(new MessageEvent('message', { source: source as MessageEventSource, data }))
}

function embedScripts() {
  return document.querySelectorAll(`script[src="${EMBED_JS}"]`)
}

describe('acceptEmbed', () => {
  it('acceptsTheOfficialShape', () => {
    const el = acceptEmbed(quote('https://www.instagram.com/p/x/')) as HTMLElement
    expect(el.tagName).toBe('BLOCKQUOTE')
    expect(el.className).toBe('instagram-media')
    expect(el.getAttribute('data-instgrm-permalink')).toBe('https://www.instagram.com/p/x/')
    expect(el.textContent).toBe('Post text')
  })

  it('stripsScripts', () => {
    const html = `<blockquote class="instagram-media" data-instgrm-permalink="https://www.instagram.com/p/x/" onclick="alert(1)">hi<script>alert(1)</script><a href="https://www.instagram.com/p/x/" onmouseover="x()">link</a></blockquote>`
    const el = acceptEmbed(html) as HTMLElement
    expect(el).not.toBeNull()
    expect(el.querySelector('script')).toBeNull()
    expect(el.outerHTML).not.toContain('onclick')
    expect(el.outerHTML).not.toContain('onmouseover')
    expect(el.outerHTML).not.toContain('alert')
  })

  it('rejectsForeignPermalink', () => {
    expect(acceptEmbed(quote('https://evil.example/'))).toBeNull()
    expect(acceptEmbed(quote('https://www.instagram.com.evil.example/p/x/'))).toBeNull()
  })

  it('rejectsTwoBlockquotes', () => {
    const one = quote('https://www.instagram.com/p/x/')
    expect(acceptEmbed(one + one)).toBeNull()
  })
})

describe('the message listener', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="root"></div>'
    listen(window, document)
  })
  afterEach(() => {
    document.head.innerHTML = ''
  })

  it('rejectsOtherOrigin', () => {
    send({}, { type: 'liberdade-instagram', html: quote('https://www.instagram.com/p/x/') })
    expect(document.querySelector('#root')?.children.length).toBe(0)
    expect(embedScripts().length).toBe(0)
  })

  it('rejectsWrongTypeAndNonObject', () => {
    send(window.parent, { type: 'other', html: quote('https://www.instagram.com/p/x/') })
    send(window.parent, 'liberdade-instagram')
    expect(document.querySelector('#root')?.children.length).toBe(0)
    expect(embedScripts().length).toBe(0)
  })

  it('loadsEmbedScriptOnce', () => {
    const data = { type: 'liberdade-instagram', html: quote('https://www.instagram.com/p/x/') }
    send(window.parent, data)
    send(window.parent, data)
    expect(embedScripts().length).toBe(1)
    expect(document.querySelectorAll('#root blockquote').length).toBe(1)
  })
})
