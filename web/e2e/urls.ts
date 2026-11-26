import { fileURLToPath } from 'node:url'

// The e2e stack runs over HTTPS with a local test certificate (`node --run e2e:cert`): WebKit on Linux
// refuses the Secure session cookie on http://localhost.

/** The app, served by `vite preview`. */
export const WEB_URL = 'https://localhost:5173'

/** The API in Docker Compose. */
export const API_URL = 'https://localhost:8080'

/** The API's management port, plain HTTP and reachable only from this machine, for the readiness check. */
export const READINESS_URL = 'http://localhost:8081/actuator/health/readiness'

/** Where `scripts/e2e-cert.mjs` writes the certificate and its key. */
export const TLS_DIR = fileURLToPath(new URL('../../.e2e-tls/', import.meta.url))
