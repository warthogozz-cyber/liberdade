// A self-signed certificate for localhost, so the e2e stack runs over HTTPS. WebKit on Linux refuses
// the Secure session cookie on http://localhost, and the iPhone project needs it.
//
// It lands in `.e2e-tls/` at the repository root, which git ignores: the API container mounts it
// (compose.e2e.yaml) and `vite preview` reads it (vite.config.ts). A test certificate, not a secret;
// an existing one is kept. Needs `openssl` on the PATH (Git for Windows ships one).
import { execFileSync } from 'node:child_process'
import { chmodSync, existsSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const dir = fileURLToPath(new URL('../../.e2e-tls/', import.meta.url))
const cert = join(dir, 'cert.pem')
const key = join(dir, 'key.pem')

if (existsSync(cert) && existsSync(key)) {
  console.log(`Certificado de teste já existe em ${dir}`)
} else {
  mkdirSync(dir, { recursive: true })
  // Spawned directly, not through a shell, so Git Bash never rewrites the "/CN=" argument as a path.
  execFileSync(
    'openssl',
    [
      'req',
      '-x509',
      '-newkey',
      'rsa:2048',
      '-nodes',
      '-days',
      '365',
      '-subj',
      '/CN=localhost',
      '-addext',
      'subjectAltName=DNS:localhost,IP:127.0.0.1',
      '-keyout',
      key,
      '-out',
      cert,
    ],
    // openssl's progress dots stay quiet; a failure still carries its message.
    { stdio: ['ignore', 'ignore', 'pipe'] },
  )
  // The API runs as another user inside its container, so the key must be readable there.
  chmodSync(key, 0o644)
  console.log(`Certificado de teste criado em ${dir}`)
}
