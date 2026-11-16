import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'
import { checkBudget } from './chunk-budget.mjs'

const dist = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const manifest = JSON.parse(readFileSync(join(dist, '.vite', 'manifest.json'), 'utf8'))
const gzipSize = (file) => gzipSync(readFileSync(join(dist, file)), { level: 9 }).length

const { ok, rows } = checkBudget(manifest, gzipSize)
console.table(rows)
if (!ok) {
  console.error('A chunk is over its budget (gzip, KB).')
  process.exit(1)
}
