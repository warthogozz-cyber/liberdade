import { API_URL } from './config'
import { errorFromResponse } from './errors'
import type { components } from './schema'

type CsrfToken = components['schemas']['CsrfTokenView']

const SAFE_METHODS = new Set(['GET', 'HEAD'])

let pending: Promise<CsrfToken> | undefined

/** Forgets the CSRF token, so the next write asks for a new one. Call it when the session changes. */
export function resetCsrf(): void {
  pending = undefined
}

/** The CSRF token, asked for once and shared by every write until reset. A failed ask is not kept. */
function csrfToken(): Promise<CsrfToken> {
  if (pending) return pending
  const request = fetch(`${API_URL}/v1/csrf`, { credentials: 'include' }).then(async (response) => {
    if (!response.ok) throw await errorFromResponse(response)
    return (await response.json()) as CsrfToken
  })
  pending = request
  request.catch(() => {
    if (pending === request) pending = undefined
  })
  return request
}

async function sendWithToken(request: Request): Promise<Response> {
  const { headerName, token } = await csrfToken()
  request.headers.set(headerName, token)
  return fetch(request)
}

/**
 * The API client's fetch. A write carries the CSRF token; when the API refuses the token
 * (403 `csrf.invalid`), it asks for a new one and sends the write once more.
 */
export async function csrfFetch(request: Request): Promise<Response> {
  if (SAFE_METHODS.has(request.method)) return fetch(request)
  const resend = request.clone()
  const response = await sendWithToken(request)
  if (response.status !== 403) return response
  const { code } = await errorFromResponse(response.clone())
  if (code !== 'csrf.invalid') return response
  resetCsrf()
  return sendWithToken(resend)
}
