const READINESS_URL = 'http://localhost:8080/actuator/health/readiness'
const INTERVAL_MS = 2_000
const TIMEOUT_MS = 120_000

async function isReady(): Promise<boolean> {
  try {
    const response = await fetch(READINESS_URL)
    if (!response.ok) return false
    const body = (await response.json()) as { status?: unknown }
    return body.status === 'UP'
  } catch {
    return false
  }
}

/** Waits for the API in Docker Compose before any test runs. */
export default async function globalSetup(): Promise<void> {
  const deadline = Date.now() + TIMEOUT_MS
  for (;;) {
    if (await isReady()) return
    if (Date.now() + INTERVAL_MS > deadline) break
    await new Promise((resolve) => setTimeout(resolve, INTERVAL_MS))
  }
  throw new Error(
    'A API não respondeu em http://localhost:8080. Suba o Compose: docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait',
  )
}
