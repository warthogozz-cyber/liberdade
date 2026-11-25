// The isolated Instagram page (D19). It runs in a sandboxed iframe without allow-same-origin, so its
// origin is "null": the parent is recognised by `event.source`, not by origin. It re-builds the
// publication as a clean blockquote before Instagram's own script (the only third-party code it loads)
// takes over, and it is served under its own CSP (see config/securityHeaders.ts).

const MESSAGE_TYPE = 'liberdade-instagram'
const PERMALINK_PREFIX = 'https://www.instagram.com/'
const EMBED_SCRIPT = 'https://www.instagram.com/embed.js'

/**
 * Parses the HTML as inert data and accepts a single `blockquote.instagram-media` whose permalink is
 * on instagram.com. Returns a fresh blockquote (class, permalink, version and text only) or null.
 */
export function acceptEmbed(html) {
  if (typeof html !== 'string') return null
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const quotes = doc.querySelectorAll('blockquote')
  if (quotes.length !== 1 || !quotes[0].classList.contains('instagram-media')) return null
  const permalink = quotes[0].getAttribute('data-instgrm-permalink')
  if (!permalink || !permalink.startsWith(PERMALINK_PREFIX)) return null

  const clean = document.createElement('blockquote')
  clean.className = 'instagram-media'
  clean.setAttribute('data-instgrm-permalink', permalink)
  const version = quotes[0].getAttribute('data-instgrm-version')
  if (version) clean.setAttribute('data-instgrm-version', version)
  const clone = quotes[0].cloneNode(true)
  for (const script of clone.querySelectorAll('script')) script.remove()
  clean.textContent = clone.textContent
  return clean
}

/** Listens on `win` for the parent's message and renders it into `#root` of `doc`. */
export function listen(win, doc) {
  win.addEventListener('message', (event) => {
    if (event.source !== win.parent) return
    const data = event.data
    if (data === null || typeof data !== 'object' || data.type !== MESSAGE_TYPE) return
    const element = acceptEmbed(data.html)
    const root = doc.getElementById('root')
    if (!element || !root) return
    root.replaceChildren(element)

    if (win.instgrm && win.instgrm.Embeds) {
      win.instgrm.Embeds.process()
    } else if (!doc.querySelector(`script[src="${EMBED_SCRIPT}"]`)) {
      const script = doc.createElement('script')
      script.async = true
      script.src = EMBED_SCRIPT
      doc.head.appendChild(script)
    }
  })
}

// Only inside a frame: opened on its own, or imported by a test, the page stays inert.
if (window.parent !== window) listen(window, document)
