const DEFAULT_CONTROLLER = 'o mantenedor do projeto Liberdade'
const DEFAULT_CONTACT = 'privacidade@liberdadefamilia.com.br'

/** An unset or blank build variable falls back to the default. */
function orDefault(value: string | undefined, fallback: string): string {
  const trimmed = value?.trim()
  return trimmed ? trimmed : fallback
}

/**
 * Who answers for the data and how to reach them, plus the versions of the two documents the web
 * shows. The versions are the API's `LegalDocuments`: the person accepts what this build displays.
 * The controller and the contact come from the build (`VITE_PRIVACY_CONTROLLER`,
 * `VITE_PRIVACY_CONTACT`), read when asked so a test can change them.
 */
export const legalConfig = {
  get controllerName(): string {
    return orDefault(import.meta.env.VITE_PRIVACY_CONTROLLER, DEFAULT_CONTROLLER)
  },
  get contactEmail(): string {
    return orDefault(import.meta.env.VITE_PRIVACY_CONTACT, DEFAULT_CONTACT)
  },
  versions: { terms: 'terms-2026-11', privacy: 'privacy-2026-11' },
  updatedAt: 'novembro de 2026',
}
