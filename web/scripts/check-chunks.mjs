import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'
import { checkBudget, composition, lazyOnly } from './chunk-budget.mjs'

const dist = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const manifest = JSON.parse(readFileSync(join(dist, '.vite', 'manifest.json'), 'utf8'))
const gzipSize = (file) => gzipSync(readFileSync(join(dist, file)), { level: 9 }).length

const { ok, rows } = checkBudget(manifest, gzipSize)
console.table(rows)

// What the initial load is made of (estimated gzip, split by rendered size), top 15 of each kind.
const modulesPath = join(dist, 'chunk-modules.json')
if (existsSync(modulesPath)) {
  const parts = composition(manifest, JSON.parse(readFileSync(modulesPath, 'utf8')), gzipSize)
  for (const kind of ['package', 'src']) {
    console.log(
      `Initial load by ${kind === 'package' ? 'node_modules package' : 'src folder'} (KB):`,
    )
    console.table(parts.filter((p) => p.kind === kind).slice(0, 15))
  }
}
// The achievement sound's synthesizer loads only when a sound plays (D13). The offline database
// and Dexie load only through `openOfflineDb()` (the lazy offline database plan, 2026-11-25).
const lazy = lazyOnly(
  manifest,
  ['src/shared/sound/synth.ts', 'src/shared/offline/db.ts', 'node_modules/dexie/dist/dexie.js'],
  existsSync(modulesPath) ? JSON.parse(readFileSync(modulesPath, 'utf8')) : {},
)
if (!lazy.ok)
  console.error('Loaded in the first load, must be on demand: ' + lazy.offenders.join(', '))
if (!ok) console.error('A chunk is over its budget (gzip, KB).')
if (!ok || !lazy.ok) process.exit(1)
