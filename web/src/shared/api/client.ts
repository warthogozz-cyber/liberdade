import createClient from 'openapi-fetch'
import { API_URL } from './config'
import { csrfFetch } from './csrf'
import { errorFromBody, fromFetchFailure, parseBody } from './errors'
import type { paths } from './schema'

export const client = createClient<paths>({
  baseUrl: API_URL,
  credentials: 'include',
  fetch: csrfFetch,
})

interface Answer<T> {
  data?: T
  error?: unknown
  response: Response
}

/** Waits for a client call and returns its data, or throws its ApiError. */
export async function unwrap<T>(call: Promise<Answer<T>>): Promise<T> {
  let answer: Answer<T>
  try {
    answer = await call
  } catch (error) {
    throw fromFetchFailure(error)
  }
  if (!answer.response.ok) throw errorFromBody(answer.response.status, answer.error)
  return answer.data as T
}

/**
 * POSTs JSON to a route outside the contract (`/webauthn/…`, `/login/webauthn`, `/logout`) and
 * returns the parsed answer: `undefined` on a 204.
 */
export function postJson(path: string, body?: unknown): Promise<unknown> {
  const request = new Request(`${API_URL}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return unwrap(
    csrfFetch(request).then(async (response) => {
      const parsed = parseBody(await response.text())
      return response.ok ? { data: parsed, response } : { error: parsed, response }
    }),
  )
}
