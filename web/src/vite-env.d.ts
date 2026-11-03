/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the API; http://localhost:8080 when unset. */
  readonly VITE_API_URL?: string
  /** 'true' starts the MSW worker before the first render (`corepack pnpm dev:mocks`). */
  readonly VITE_API_MOCKS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
