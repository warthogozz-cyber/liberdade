/**
 * A failed API call. `code` is the problem code the screens branch on: the API's own code, `'network'`
 * (status 0) when the request never got an answer, or `'http'` when the answer had no problem body.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly detail?: string
  /**
   * The problem's properties beyond `type`, `title`, `status`, `detail`, `code` and `instance`, such as
   * the `current` transaction of a version conflict. Empty when there are none.
   */
  readonly extra: Readonly<Record<string, unknown>>

  constructor(
    status: number,
    code: string,
    detail?: string,
    extra: Readonly<Record<string, unknown>> = {},
  ) {
    super(detail ?? code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
    this.extra = extra
  }
}

const STANDARD_PROPERTIES = new Set(['type', 'title', 'status', 'detail', 'code', 'instance'])

interface Problem {
  code: string
  detail?: unknown
}

function isProblem(body: unknown): body is Problem {
  return typeof body === 'object' && body !== null && typeof (body as Problem).code === 'string'
}

/** The ApiError for a failed answer, from its parsed body. */
export function errorFromBody(status: number, body: unknown): ApiError {
  if (!isProblem(body)) return new ApiError(status, 'http')
  const extra = Object.fromEntries(
    Object.entries(body).filter(([key]) => !STANDARD_PROPERTIES.has(key)),
  )
  return new ApiError(
    status,
    body.code,
    typeof body.detail === 'string' ? body.detail : undefined,
    extra,
  )
}

/** Parses an answer's text as JSON when it is JSON. An empty text is `undefined`. */
export function parseBody(text: string): unknown {
  if (text === '') return undefined
  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

/** Reads a failed response into its ApiError. */
export async function errorFromResponse(response: Response): Promise<ApiError> {
  return errorFromBody(response.status, parseBody(await response.text()))
}

/** fetch rejects with a TypeError when the request got no answer; anything else passes unchanged. */
export function fromFetchFailure(error: unknown): unknown {
  return error instanceof TypeError ? new ApiError(0, 'network') : error
}
