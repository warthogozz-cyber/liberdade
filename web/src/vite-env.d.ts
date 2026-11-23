/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  /** Base URL of the API; http://localhost:8080 when unset. */
  readonly VITE_API_URL?: string
  /** 'true' starts the MSW worker before the first render (`corepack pnpm dev:mocks`). */
  readonly VITE_API_MOCKS?: string
  /** Who controls the data, shown in the privacy policy; empty or unset uses the default. */
  readonly VITE_PRIVACY_CONTROLLER?: string
  /** The controller's CPF or CNPJ with its label ("CNPJ ..."), naming the seller in the terms; empty or unset uses a neutral placeholder. */
  readonly VITE_PRIVACY_CONTROLLER_DOCUMENT?: string
  /** The controller's physical address, naming the seller in the terms; empty or unset uses a neutral placeholder. */
  readonly VITE_PRIVACY_CONTROLLER_ADDRESS?: string
  /** The privacy contact e-mail, shown in the policy and the terms; empty or unset uses the default. */
  readonly VITE_PRIVACY_CONTACT?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
