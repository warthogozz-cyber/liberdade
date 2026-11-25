import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { isAbsolute, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { VitePWA } from 'vite-plugin-pwa'
import { securityHeadersPlugin } from './config/securityHeaders.ts'
import type { Plugin } from 'vite'
import { defineConfig } from 'vitest/config'

const projectRoot = fileURLToPath(new URL('./', import.meta.url))

/**
 * Absolute ids become relative to the web project, with forward slashes, so the public file holds
 * no build-machine path. Virtual ids (a leading NUL byte, or not absolute) stay as they are.
 */
function publicId(id: string): string {
  if (id.startsWith('\0') || !isAbsolute(id)) return id
  return relative(projectRoot, id).replaceAll('\\', '/')
}

/**
 * Writes `dist/chunk-modules.json`: each emitted chunk's file mapped to its modules and their
 * rendered sizes in bytes. The Vite manifest has no module map, so `check:chunks` reads this to say
 * what the initial load is made of.
 */
function chunkModules(): Plugin {
  return {
    name: 'chunk-modules',
    generateBundle(_options, bundle) {
      const map: Record<string, Record<string, number>> = {}
      for (const [file, item] of Object.entries(bundle)) {
        if (item.type !== 'chunk') continue
        map[file] = Object.fromEntries(
          Object.entries(item.modules).map(([id, info]) => [publicId(id), info.renderedLength]),
        )
      }
      this.emitFile({
        type: 'asset',
        fileName: 'chunk-modules.json',
        source: JSON.stringify(map),
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    chunkModules(),
    securityHeadersPlugin(),
    // Installable, with the app shell cached (D27). `main.tsx` registers the worker itself.
    VitePWA({
      registerType: 'autoUpdate',
      // The brief's `null` is deprecated in vite-plugin-pwa 2; `false` is its equivalent.
      injectRegister: false,
      workbox: {
        navigateFallback: '/index.html',
        // The API's browser routes reach the server, never the app shell (T10-fallback-denylist).
        navigateFallbackDenylist: [/^\/(oauth2|login|logout|webauthn)(\/|$)/, /^\/embed\//],
        // The fonts too, so the first screen offline keeps Inter (T10-precache-fonts).
        globPatterns: ['**/*.{js,css,html,woff2}'],
        // MSW's worker ships in `public/` for `dev:mocks`; the app's worker never caches it. The isolated
        // Instagram page stays out too: its CSP is its security boundary and a cached copy could lose it.
        globIgnores: ['**/mockServiceWorker.js', 'embed/**'],
        // The first visit is controlled as soon as the worker activates, so a screen opened offline right
        // after it (a lazy chunk never fetched online) still comes from the precache.
        clientsClaim: true,
      },
      manifest: {
        name: 'Liberdade',
        short_name: 'Liberdade',
        description: 'As finanças da sua família, simples e em paz.',
        lang: 'pt-BR',
        start_url: '/',
        display: 'standalone',
        // The primary and background colors of `index.css`.
        theme_color: '#0e5a66',
        background_color: '#f4f8f8',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'maskable-icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  // The E2E suite expects this port; a busy port fails instead of moving elsewhere.
  // `check:chunks` reads the manifest to enforce the size budget.
  build: {
    manifest: true,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              // The packages every screen needs, kept in one chunk: a lazy import that reaches only some
              // of them would otherwise split them in several, and each split costs the first load
              // gzip overhead (T14 measured 1.5 KB). Radix is not here: its dialogs are only for lazy screens, so
              // it splits on its own and stays out of the first load (Task 6 of Plan 5). Dexie is not here
              // either: only the lazy offline database loads it (the lazy offline database plan).
              name: 'vendor',
              test: /node_modules[\\/](react|react-dom|react-router|react-intl|@formatjs|intl-messageformat|@tanstack|scheduler|openapi-fetch|uuid)[\\/]/,
            },
          ],
        },
      },
    },
  },
  server: { port: 5173, strictPort: true },
  preview: { port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    // Only the light theme's source is read as text (drawPortrait.test.ts); other CSS stays empty.
    css: { include: [/src[\\/]index\.css/] },
    include: [
      'src/**/*.test.{ts,tsx}',
      'eslint/**/*.test.js',
      'scripts/**/*.test.mjs',
      'config/**/*.test.ts',
    ],
    coverage: {
      provider: 'v8',
      include: ['src/**'],
      exclude: [
        'src/main.tsx',
        'src/**/*.test.*',
        'src/test-setup.ts',
        'src/vite-env.d.ts',
        'src/shared/api/schema.ts',
        'src/shared/testing/**',
        'src/mocks/**',
      ],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
        'src/shared/**': { lines: 90, functions: 90, branches: 90, statements: 90 },
      },
    },
  },
})
